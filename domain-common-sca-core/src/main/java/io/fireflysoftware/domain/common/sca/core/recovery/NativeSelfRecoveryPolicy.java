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

import io.fireflysoftware.domain.common.sca.core.recovery.http.NativeRecoveryHttpPort;
import org.fireflyframework.security.api.domain.*;
import org.fireflyframework.security.spi.PolicyDecisionPort;
import reactor.core.publisher.Mono;
import java.time.*;
import java.util.*;

/** Optional owning-service policy for this exact native self-recovery purpose; it grants no general business authority. */
public final class NativeSelfRecoveryPolicy implements PolicyDecisionPort {
    private final NativeRecoveryPort nativePort;
    private final RecoveryTrust trust;
    private final Clock clock;
    public NativeSelfRecoveryPolicy(NativeRecoveryPort nativePort,RecoveryTrust trust,Clock clock){
        if(!(nativePort instanceof NativeRecoveryHttpPort) || !nativePort.isQualified())throw new IllegalStateException("Actual native HTTP owner required for self-recovery policy");
        this.nativePort=nativePort;this.trust=Objects.requireNonNull(trust);this.clock=Objects.requireNonNull(clock);
    }
    @Override public Mono<Decision> authorize(SecurityPrincipal principal,String action,String resource,Map<String,Object> context){
        return Mono.defer(()->{
            RecoveryAction phase=Arrays.stream(RecoveryAction.values()).filter(value->value.policyAction().equals(action)).findFirst().orElseThrow(RecoveryFailure::refused);
            if(!("sca.recovery:"+trust.purpose()).equals(resource) || context==null
                || !context.keySet().equals(Set.of("issuer","tenantId","actorSubject","clientId","principalKind","purpose","operationId","lockGeneration")))throw RecoveryFailure.refused();
            RecoveryAdmission.validate(principal,trust,clock);
            if(!principal.issuer().equals(context.get("issuer")) || !principal.tenantId().equals(context.get("tenantId"))
                || !principal.subject().equals(context.get("actorSubject")) || !principal.claim("client_id",String.class).equals(context.get("clientId"))
                || !trust.population().equals(context.get("principalKind")) || !trust.purpose().equals(context.get("purpose"))
                || !principal.claim("recovery_lock_generation",String.class).equals(context.get("lockGeneration")))throw RecoveryFailure.refused();
            Object operationValue=context.get("operationId");
            UUID operation=null;
            if(phase==RecoveryAction.BEGIN){if(!"new".equals(operationValue))throw RecoveryFailure.refused();}
            else {if(!(operationValue instanceof String id))throw RecoveryFailure.refused();operation=UUID.fromString(id);if(!operation.toString().equals(id))throw RecoveryFailure.refused();}
            UUID actualOperation=operation;
            return Mono.defer(()->nativePort.admit(principal,trust.purpose(),phase,actualOperation)).timeout(Duration.ofSeconds(5))
                .map(current->{RecoveryAdmission.validate(principal,trust,clock);RecoveryAdmission.validateContext(principal,current,trust,clock);return Decision.permit();});
        }).switchIfEmpty(Mono.just(Decision.deny("Recovery policy refused"))).onErrorReturn(Decision.deny("Recovery policy refused"));
    }
}
