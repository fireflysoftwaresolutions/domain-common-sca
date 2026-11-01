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

import io.fireflysoftware.domain.common.sca.core.recovery.http.NativeRecoveryHttpPortImpl;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Policy unit tests use an external transport double, never an installed permit provider or native acceptance fixture. */
class NativeSelfRecoveryPolicyTest {
    static final UUID OPERATION=UUID.fromString("d89c7273-40cd-4827-baf9-e22862799859");
    static Map<String,Object> context(){return Map.of("issuer",RecoveryAdmissionTest.ISSUER,"tenantId","opaque-tenant","actorSubject","opaque-subject",
        "clientId","configured-ceremony-client","principalKind","HUMAN","purpose",RecoveryAdmissionTest.PURPOSE,
        "operationId",OPERATION.toString(),"lockGeneration","generation-1");}
    @Test void exactRecoveryActionAndServerContextRequireCurrentNativeOwnerBeforePermit() {
        var nativePort=mock(NativeRecoveryHttpPortImpl.class);when(nativePort.isQualified()).thenReturn(true);
        when(nativePort.admit(any(),eq(RecoveryAdmissionTest.PURPOSE),eq(RecoveryAction.CONSUME),eq(OPERATION))).thenReturn(Mono.just(RecoveryAdmissionTest.context()));
        var policy=new NativeSelfRecoveryPolicyImpl(nativePort,RecoveryAdmissionTest.trust(),Clock.fixed(RecoveryAdmissionTest.NOW,ZoneOffset.UTC));
        StepVerifier.create(policy.authorize(RecoveryAdmissionTest.actor(Map.of()),"sca.recovery.authorization.consume","sca.recovery:"+RecoveryAdmissionTest.PURPOSE,context()))
            .assertNext(decision->{assertThat(decision.granted()).isTrue();assertThat(decision.obligations()).isEmpty();}).verifyComplete();
        when(nativePort.admit(any(),anyString(),any(),any())).thenReturn(Mono.error(new IllegalStateException("private")));
        StepVerifier.create(policy.authorize(RecoveryAdmissionTest.actor(Map.of()),"sca.recovery.authorization.consume","sca.recovery:"+RecoveryAdmissionTest.PURPOSE,context()))
            .assertNext(decision->assertThat(decision.granted()).isFalse()).verifyComplete();
    }
    @Test void financialUnknownMachineOrForgedServerContextNeverCallsNativeOwner() {
        var nativePort=mock(NativeRecoveryHttpPortImpl.class);when(nativePort.isQualified()).thenReturn(true);
        var policy=new NativeSelfRecoveryPolicyImpl(nativePort,RecoveryAdmissionTest.trust(),Clock.fixed(RecoveryAdmissionTest.NOW,ZoneOffset.UTC));
        for(String action:List.of("payments.execute","sca.recovery.unknown"))
            StepVerifier.create(policy.authorize(RecoveryAdmissionTest.actor(Map.of()),action,"sca.recovery:"+RecoveryAdmissionTest.PURPOSE,context()))
                .assertNext(decision->assertThat(decision.granted()).isFalse()).verifyComplete();
        var forged=new HashMap<>(context());forged.put("tenantId","other-tenant");
        StepVerifier.create(policy.authorize(RecoveryAdmissionTest.actor(Map.of()),"sca.recovery.authorization.consume","sca.recovery:"+RecoveryAdmissionTest.PURPOSE,forged))
            .assertNext(decision->assertThat(decision.granted()).isFalse()).verifyComplete();
        StepVerifier.create(policy.authorize(RecoveryAdmissionTest.actor(Map.of("principal_kind","MACHINE")),"sca.recovery.authorization.consume","sca.recovery:"+RecoveryAdmissionTest.PURPOSE,context()))
            .assertNext(decision->assertThat(decision.granted()).isFalse()).verifyComplete();
        verify(nativePort,never()).admit(any(),anyString(),any(),any());
    }
}
