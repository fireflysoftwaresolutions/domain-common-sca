/*
 * Copyright 2025 Firefly Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package io.fireflysoftware.domain.common.sca.core.recovery;

import io.fireflysoftware.common.sca.sdk.api.ScaRecoveryApi;
import io.fireflysoftware.common.sca.sdk.model.*;
import org.fireflyframework.security.api.domain.Decision;
import org.fireflyframework.security.api.domain.SecurityPrincipal;
import org.fireflyframework.security.spi.PolicyDecisionPort;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RecoveryOrchestratorTest {
    final UUID operation=UUID.fromString("d89c7273-40cd-4827-baf9-e22862799859");
    final UUID execution=UUID.fromString("c10eceaa-f26b-4964-bd76-93c504250a8e");
    final ScaRecoveryApi api=mock(ScaRecoveryApi.class);
    RecoveryOrchestrator service(NativeRecoveryPort nativePort) {
        var policy=mock(PolicyDecisionPort.class);
        when(policy.authorize(any(),anyString(),anyString(),anyMap())).thenReturn(Mono.just(Decision.permit()));
        var guard=new RecoveryAdmission(()->Mono.just(RecoveryAdmissionTest.actor(Map.of())),policy,nativePort,
            RecoveryAdmissionTest.audit(),RecoveryAdmissionTest.trust(),Clock.fixed(RecoveryAdmissionTest.NOW,ZoneOffset.UTC));
        return new RecoveryOrchestrator(api,guard,nativePort,Clock.fixed(RecoveryAdmissionTest.NOW,ZoneOffset.UTC));
    }
    @Test void consumeReturnsOnlyBoundReceiptAndNeverExecutesOrReadsNativeJournal() {
        var port=spy(RecoveryAdmissionTest.nativePort(new AtomicInteger()));
        var binding=RecoveryAdmissionTest.context().coreBinding();
        when(api.consumeRecoveryAuthorization(eq(operation),any())).thenReturn(Mono.just(new RecoveryAuthorizationReceipt()
            .operationId(operation).executionId(execution).binding(binding)
            .expiresAt(LocalDateTime.ofInstant(RecoveryAdmissionTest.NOW.plusSeconds(30),ZoneOffset.UTC))));
        StepVerifier.create(service(port).consume(operation)).assertNext(value->{
            assertThat(value.operationId()).isEqualTo(operation); assertThat(value.executionId()).isEqualTo(execution);
        }).verifyComplete();
        verify(port,never()).journal(any(),any(),any());
    }
    @Test void foreignReceiptAndNativeJournalMismatchNeverRecordOrUnlock() {
        var authorization=new RecoveryAuthorization(operation,execution,RecoveryAdmissionTest.context().coreBinding(),RecoveryAdmissionTest.NOW.plusSeconds(30));
        var port=new NativeRecoveryPort(){
            public boolean isQualified(){return true;}
            public Mono<NativeRecoveryContext> admit(SecurityPrincipal p,String purpose,RecoveryAction action,java.util.UUID operationId){return Mono.just(RecoveryAdmissionTest.context());}
            public Mono<NativeExecutionJournal> journal(SecurityPrincipal p,NativeRecoveryContext c,RecoveryAuthorization a){
                return Mono.just(new NativeExecutionJournal(UUID.randomUUID(),"SUCCEEDED"));
            }
        };
        StepVerifier.create(service(port).record(operation,authorization,"SUCCEEDED")).expectError(RecoveryFailure.class).verify();
        verifyNoInteractions(api);
    }
}
