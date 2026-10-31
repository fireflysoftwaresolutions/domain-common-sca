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

class RecoveryAdmissionTest {
    static final Instant NOW = Instant.parse("2026-10-31T20:00:00Z");
    static final String ISSUER = "https://identity.example/realms/customers";
    static final String PURPOSE = "PERMANENT_AUTHENTICATION_UNLOCK";
    static final String ACTOR = "09f51194-de36-4cb7-b33a-b352dc4fce20";
    static final Set<String> AUDIENCES = Set.of("firefly-recovery-domain", "firefly-recovery-core");
    static SecurityPrincipal actor(Map<String,Object> changes) {
        var claims = new HashMap<String,Object>();
        claims.put("iss",ISSUER); claims.put("sub","opaque-subject"); claims.put("tenant_id","opaque-tenant");
        claims.put("client_id","configured-ceremony-client"); claims.put("principal_kind","HUMAN"); claims.put("party_id",ACTOR);
        claims.put("aud",AUDIENCES); claims.put("exp",NOW.plusSeconds(120)); claims.put("iat",NOW); claims.put("jti","assertion-id");
        claims.put("recovery_only",true); claims.put("recovery_purpose",PURPOSE); claims.put("recovery_ceremony_id","ceremony-id");
        claims.put("recovery_lock_generation","generation-1"); claims.put("recovery_realm","customers"); claims.putAll(changes);
        changes.entrySet().stream().filter(e -> e.getValue().equals("<absent>")).forEach(e -> claims.remove(e.getKey()));
        return SecurityPrincipal.builder().issuer(ISSUER).subject("opaque-subject").tenantId("opaque-tenant")
            .claims(claims).scopes(Set.of("firefly.recovery")).build();
    }
    static NativeRecoveryContext context() {
        return new NativeRecoveryContext(ISSUER,"opaque-tenant","configured-ceremony-client","opaque-subject","customers","HUMAN",
            ACTOR,"ceremony-id","generation-1",PURPOSE,NOW.plusSeconds(60));
    }
    static RecoveryTrust trust() { return new RecoveryTrust(ISSUER,"customers","HUMAN",AUDIENCES,
        Set.of("configured-ceremony-client"),PURPOSE,300); }
    static RecoveryAuditPort audit() { return new RecoveryAuditPort() {
        public boolean isQualified(){return true;}
        public Mono<Void> record(Event e){return Mono.empty();}
    }; }
    static NativeRecoveryPort nativePort(AtomicInteger calls) { return new NativeRecoveryPort() {
        public boolean isQualified(){return true;}
        public Mono<NativeRecoveryContext> admit(SecurityPrincipal p,String purpose,RecoveryAction action,java.util.UUID operationId){calls.incrementAndGet();return Mono.just(context());}
        public Mono<NativeExecutionJournal> journal(SecurityPrincipal p,NativeRecoveryContext c,RecoveryAuthorization a){return Mono.error(new IllegalStateException());}
    }; }
    @Test void missingOrOrdinaryOrCrossPopulationPrincipalNeverReachesNativeAdmission() {
        var calls=new AtomicInteger(); var policy=mock(PolicyDecisionPort.class);
        var absent=new RecoveryAdmission(Mono::empty,policy,nativePort(calls),audit(),trust(),Clock.fixed(NOW,ZoneOffset.UTC));
        StepVerifier.create(absent.admit(RecoveryAction.BEGIN,null)).expectError(RecoveryFailure.class).verify();
        for(var changes:List.of(Map.<String,Object>of("recovery_only",false), Map.<String,Object>of("client_id","other"),
            Map.<String,Object>of("principal_kind","STAFF"),Map.<String,Object>of("staff_actor_id",ACTOR),
            Map.<String,Object>of("aud",Set.of("firefly-recovery-domain","business-api")),
            Map.<String,Object>of("iss","https://untrusted.example"), Map.<String,Object>of("tenant_id","other-tenant"),
            Map.<String,Object>of("roles",List.of("admin")),Map.<String,Object>of("recovery_ceremony_id","<absent>"),
            Map.<String,Object>of("iat",123L),Map.<String,Object>of("exp",NOW),Map.<String,Object>of("nbf",NOW.plusSeconds(1)),Map.<String,Object>of("client_id","<absent>"))) {
            var guard=new RecoveryAdmission(()->Mono.just(actor(changes)),policy,nativePort(calls),audit(),trust(),Clock.fixed(NOW,ZoneOffset.UTC));
            StepVerifier.create(guard.admit(RecoveryAction.BEGIN,null)).expectError(RecoveryFailure.class).verify();
        }
        assertThat(calls.get()).isZero(); verifyNoInteractions(policy);
    }
    @Test void independentNativeStateAndCurrentPolicyAreRequiredForEveryAdmission() {
        var policy=mock(PolicyDecisionPort.class); var calls=new AtomicInteger();
        when(policy.authorize(any(),eq("sca.recovery.create"),eq("sca.recovery:"+PURPOSE),anyMap()))
            .thenReturn(Mono.just(Decision.deny("refused")));
        var guard=new RecoveryAdmission(()->Mono.just(actor(Map.of())),policy,nativePort(calls),audit(),trust(),Clock.fixed(NOW,ZoneOffset.UTC));
        StepVerifier.create(guard.admit(RecoveryAction.BEGIN,null)).expectError(RecoveryFailure.class).verify();
        assertThat(calls.get()).isEqualTo(1);
        when(policy.authorize(any(),anyString(),anyString(),anyMap())).thenReturn(Mono.error(new IllegalStateException("private provider URL")));
        StepVerifier.create(guard.admit(RecoveryAction.BEGIN,null)).expectErrorMatches(e->e instanceof RecoveryFailure && !e.toString().contains("private")).verify();
    }
    @Test void auditFailureAndUnqualifiedPortsCannotAdmit() {
        var sink=new RecoveryAuditPort(){ public boolean isQualified(){return true;} public Mono<Void> record(Event e){return Mono.error(new IllegalStateException("secret"));} };
        var policy=mock(PolicyDecisionPort.class);
        when(policy.authorize(any(),anyString(),anyString(),anyMap())).thenReturn(Mono.just(Decision.permit()));
        var guard=new RecoveryAdmission(()->Mono.just(actor(Map.of())),policy,nativePort(new AtomicInteger()),sink,trust(),Clock.fixed(NOW,ZoneOffset.UTC));
        StepVerifier.create(guard.admit(RecoveryAction.BEGIN,null)).expectError(RecoveryFailure.class).verify();
        assertThatThrownBy(()->new RecoveryAdmission(Mono::empty,policy,mock(NativeRecoveryPort.class),audit(),trust(),Clock.systemUTC()))
            .isInstanceOf(IllegalStateException.class);
    }
    @Test void policyIndeterminateObligationsAndEmptyDecisionDeny() {
        for(var result:List.of(Mono.just(Decision.indeterminate("indeterminate")),Mono.just(Decision.permit(List.of(
                org.fireflyframework.security.api.domain.Obligation.of("unsupported",Map.of())))),Mono.<Decision>empty())) {
            var policy=mock(PolicyDecisionPort.class);when(policy.authorize(any(),anyString(),anyString(),anyMap())).thenReturn(result);
            var guard=new RecoveryAdmission(()->Mono.just(actor(Map.of())),policy,nativePort(new AtomicInteger()),audit(),trust(),Clock.fixed(NOW,ZoneOffset.UTC));
            StepVerifier.create(guard.admit(RecoveryAction.BEGIN,null)).expectError(RecoveryFailure.class).verify();
        }
    }
    @Test void expiryDuringPolicyEvaluationCannotReachDownstreamAdmission() {
        var now=new java.util.concurrent.atomic.AtomicReference<>(NOW);
        Clock clock=new Clock(){public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId zone){return this;}public Instant instant(){return now.get();}};
        var policy=mock(PolicyDecisionPort.class);
        when(policy.authorize(any(),anyString(),anyString(),anyMap())).thenAnswer(call->{now.set(NOW.plusSeconds(121));return Mono.just(Decision.permit());});
        var guard=new RecoveryAdmission(()->Mono.just(actor(Map.of())),policy,nativePort(new AtomicInteger()),audit(),trust(),clock);
        StepVerifier.create(guard.admit(RecoveryAction.BEGIN,null)).expectError(RecoveryFailure.class).verify();
    }
}
