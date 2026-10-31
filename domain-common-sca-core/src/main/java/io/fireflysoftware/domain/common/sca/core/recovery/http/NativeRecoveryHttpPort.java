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


package io.fireflysoftware.domain.common.sca.core.recovery.http;

import io.fireflysoftware.domain.common.sca.core.recovery.*;
import io.fireflysoftware.common.sca.sdk.model.RecoveryBinding;
import org.fireflyframework.security.api.domain.SecurityPrincipal;
import reactor.core.publisher.Mono;
import java.time.Instant;
import java.util.Objects;

/** Qualified protocol implementation; deployment still requires actual provider/consumer acceptance. */
public final class NativeRecoveryHttpPort implements NativeRecoveryPort {
    private final NativeRecoveryHttpClient http;
    private final RecoveryTrust trust;
    public NativeRecoveryHttpPort(NativeRecoveryHttpClient http,RecoveryTrust trust){this.http=http;this.trust=trust;}
    @Override public boolean isQualified(){return true;}
    @Override public Mono<NativeRecoveryContext> admit(SecurityPrincipal actor,String purpose,RecoveryAction action,java.util.UUID operationId){
        RecoveryBinding expected=new RecoveryBinding().issuer(actor.issuer()).tenant(actor.tenantId()).clientId(actor.claim("client_id",String.class))
            .actorSubject(actor.subject()).targetIssuer(actor.issuer()).targetSubject(actor.subject()).targetRealm(trust.realm()).population(trust.population())
            .purpose(purpose).lockGeneration(actor.claim("recovery_lock_generation",String.class));
        return http.assertion(true).flatMap(assertion->http.post("admit",new NativeRecoveryHttpClient.Rpc(assertion,expected,operationId,null,null,null,null,null,null,null,action.nativeAction()),
            NativeRecoveryHttpClient.Admission.class)).map(admission->{
                if(!expected.equals(admission.binding()) || blank(admission.ownerLinkId()) || blank(admission.identityLinkAttestationId()) || blank(admission.lockAttestationId())
                    || admission.validUntil()==null || !admission.validUntil().isAfter(Instant.now()) || !action.nativeAction().equals(admission.action()))throw RecoveryFailure.refused();
                return new NativeRecoveryContext(actor.issuer(),actor.tenantId(),expected.getClientId(),actor.subject(),trust.realm(),trust.population(),
                    actor.claim(trust.population().equals("HUMAN")?"party_id":"staff_actor_id",String.class),actor.claim("recovery_ceremony_id",String.class),
                    expected.getLockGeneration(),purpose,admission.validUntil());
            });
    }
    @Override public Mono<NativeExecutionJournal> journal(SecurityPrincipal actor,NativeRecoveryContext context,RecoveryAuthorization authorization){
        return http.assertion(true).flatMap(assertion->http.post("journal",new NativeRecoveryHttpClient.Rpc(assertion,context.coreBinding(),authorization.operationId(),null,null,null,null,null,
            authorization.executionId(),null,"RECORD_EXECUTION"),NativeRecoveryHttpClient.Execution.class)).map(journal->{
                if(!context.coreBinding().equals(journal.binding()) || !Objects.equals(authorization.executionId(),journal.executionId())
                    || journal.outcome()==null)throw RecoveryFailure.refused();return new NativeExecutionJournal(journal.executionId(),journal.outcome());
            });
    }
    private static boolean blank(String value){return value==null || value.isBlank();}
}
