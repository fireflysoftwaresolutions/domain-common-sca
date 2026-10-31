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

import org.fireflyframework.security.api.domain.SecurityPrincipal;
import org.fireflyframework.security.spi.PolicyDecisionPort;
import org.fireflyframework.security.spi.SecurityContextPort;
import reactor.core.publisher.Mono;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/** Generic verified-principal/current-policy admission plus independent native self-only current-state proof. */
public final class RecoveryAdmission {
    private final SecurityContextPort security;
    private final PolicyDecisionPort policy;
    private final NativeRecoveryPort nativePort;
    private final RecoveryAuditPort audit;
    private final RecoveryTrust trust;
    private final Clock clock;
    public RecoveryAdmission(SecurityContextPort security,PolicyDecisionPort policy,NativeRecoveryPort nativePort,
            RecoveryAuditPort audit,RecoveryTrust trust,Clock clock) {
        if (security==null || policy==null || nativePort==null || !nativePort.isQualified() || audit==null || !audit.isQualified())
            throw new IllegalStateException("Qualified native admission and durable audit required");
        this.security=security; this.policy=policy; this.nativePort=nativePort; this.audit=audit; this.trust=Objects.requireNonNull(trust); this.clock=Objects.requireNonNull(clock);
    }
    public record Caller(SecurityPrincipal principal,NativeRecoveryContext context) {
        @Override public String toString() { return "RecoveryCaller[redacted]"; }
    }
    public Mono<Caller> admit(RecoveryAction action,UUID operation) {
        return Mono.defer(()->{
            AtomicReference<RecoveryAuditPort.Actor> actor=new AtomicReference<>();
            Mono<Caller> admission=Mono.defer(security::currentPrincipal)
                .switchIfEmpty(Mono.error(new RecoveryFailure(RecoveryFailure.Kind.UNAUTHENTICATED)))
                .flatMap(p->{
                    validate(p);
                    actor.set(new RecoveryAuditPort.Actor(p.issuer(),p.tenantId(),p.subject(),p.claim("client_id",String.class),p.claim("principal_kind",String.class)));
                    return Mono.defer(()->nativePort.admit(p,trust.purpose(),action,operation)).timeout(Duration.ofSeconds(5))
                        .switchIfEmpty(Mono.error(RecoveryFailure.refused())).flatMap(context->{
                            validateContext(p,context);
                            Map<String,Object> policyContext=Map.of("issuer",p.issuer(),"tenantId",p.tenantId(),"actorSubject",p.subject(),
                                "clientId",p.claim("client_id",String.class),"principalKind",trust.population(),"purpose",trust.purpose(),
                                "operationId",operation==null?"new":operation.toString(),"lockGeneration",context.lockGeneration());
                            return Mono.defer(()->policy.authorize(p,action.policyAction(),"sca.recovery:"+trust.purpose(),policyContext))
                                .timeout(Duration.ofSeconds(5)).filter(d->d.granted() && d.obligations().isEmpty())
                                .switchIfEmpty(Mono.error(RecoveryFailure.refused())).map(d->{validate(p);validateContext(p,context);return new Caller(p,context);});
                        });
                }).onErrorMap(e->e instanceof RecoveryFailure?e:RecoveryFailure.refused());
            return admission.materialize().flatMap(signal->Mono.defer(()->audit.record(new RecoveryAuditPort.Event(actor.get(),action,
                    signal.isOnError()?RecoveryAuditPort.Outcome.REFUSED:RecoveryAuditPort.Outcome.ADMITTED)))
                .timeout(Duration.ofSeconds(5)).onErrorMap(e->RecoveryFailure.unavailable())
                .then(signal.isOnError()?Mono.error(signal.getThrowable()):Mono.just(signal.get())));
        });
    }
    private void validate(SecurityPrincipal p) {
        var c=p.claims(); Instant now=clock.instant();
        String kind=p.claim("principal_kind",String.class), id=p.claim(trust.population().equals("HUMAN")?"party_id":"staff_actor_id",String.class);
        if (!trust.issuer().equals(p.issuer()) || !trust.issuer().equals(c.get("iss")) || blank(p.subject()) || !p.subject().equals(c.get("sub"))
                || blank(p.tenantId()) || !p.tenantId().equals(c.get("tenant_id")) || !trust.population().equals(kind)
                || !canonicalUuid(id) || c.containsKey(trust.population().equals("HUMAN")?"staff_actor_id":"party_id")
                || !(c.get("client_id") instanceof String client) || !trust.clients().contains(client)
                || !(c.get("aud") instanceof Collection<?> aud) || aud.size()!=trust.audiences().size() || !new HashSet<>(aud).equals(trust.audiences())
                || !Boolean.TRUE.equals(c.get("recovery_only")) || !trust.purpose().equals(c.get("recovery_purpose"))
                || !trust.realm().equals(c.get("recovery_realm")) || blank(p.claim("recovery_ceremony_id",String.class))
                || blank(p.claim("recovery_lock_generation",String.class)) || blank(p.claim("jti",String.class))
                || !p.scopes().equals(Set.of("firefly.recovery")) || !p.authorities().isEmpty()
                || Set.of("roles","groups","realm_access","resource_access","sid","session_state").stream().anyMatch(c::containsKey)
                || !(c.get("exp") instanceof Instant exp) || !exp.isAfter(now)
                || !(c.get("iat") instanceof Instant issued) || issued.isAfter(now) || !exp.isAfter(issued)
                || Duration.between(issued,exp).getSeconds()>trust.maxLifetimeSeconds()
                || c.containsKey("nbf") && (!(c.get("nbf") instanceof Instant nbf) || nbf.isAfter(now))) throw RecoveryFailure.refused();
    }
    private void validateContext(SecurityPrincipal p,NativeRecoveryContext c) {
        if (c==null || !Objects.equals(c.issuer(),p.issuer()) || !Objects.equals(c.tenant(),p.tenantId())
                || !Objects.equals(c.subject(),p.subject()) || !Objects.equals(c.clientId(),p.claim("client_id",String.class))
                || !trust.realm().equals(c.realm()) || !trust.population().equals(c.population()) || !trust.purpose().equals(c.purpose())
                || !Objects.equals(c.actorId(),p.claim(trust.population().equals("HUMAN")?"party_id":"staff_actor_id",String.class))
                || !Objects.equals(c.ceremonyId(),p.claim("recovery_ceremony_id",String.class))
                || !Objects.equals(c.lockGeneration(),p.claim("recovery_lock_generation",String.class))
                || c.validUntil()==null || !c.validUntil().isAfter(clock.instant())) throw RecoveryFailure.refused();
    }
    private static boolean blank(String s){return s==null || s.isBlank();}
    private static boolean canonicalUuid(String s){try{return s!=null && UUID.fromString(s).toString().equals(s);}catch(IllegalArgumentException e){return false;}}
}
