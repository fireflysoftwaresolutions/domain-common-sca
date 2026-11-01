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

import io.fireflysoftware.common.sca.sdk.model.RecoveryBinding;
import io.fireflysoftware.domain.common.sca.core.recovery.*;
import org.springframework.http.*;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import java.net.URI;
import java.time.*;
import java.util.UUID;

/** Product protocol transport. Native credentials are never requested or transported here. */
public final class NativeRecoveryHttpClientImpl {
    public record Rpc(String actorAssertion,RecoveryBinding binding,UUID operationId,UUID challengeId,UUID reservationId,
        String providerChallengeId,String category,String attestation,UUID executionId,String outcome,String action){
        @Override public String toString(){return "NativeRpc[redacted]";}
    }
    public record Admission(RecoveryBinding binding,String ownerLinkId,String identityLinkAttestationId,String lockAttestationId,Instant validUntil,String action){
        @Override public String toString(){return "NativeAdmission[redacted]";}
    }
    public record Execution(RecoveryBinding binding,UUID executionId,String outcome){
        @Override public String toString(){return "NativeExecution[redacted]";}
    }
    public record AuditReceipt(UUID auditId,Instant committedAt){ }
    private final WebClient web;
    private final URI base;
    private final String username;
    private final String password;
    private final Duration timeout;
    public NativeRecoveryHttpClientImpl(WebClient web,URI base,String username,String password,Duration timeout){
        if(web==null || base==null || !"https".equals(base.getScheme()) || base.getHost()==null || base.getUserInfo()!=null
            || base.getRawQuery()!=null || base.getFragment()!=null || username==null || username.isBlank() || username.contains(":")
            || password==null || password.isBlank() || timeout==null || timeout.isZero() || timeout.isNegative() || timeout.compareTo(Duration.ofSeconds(10))>0)
            throw new IllegalArgumentException("Explicit HTTPS native workload transport required");
        this.web=web;this.base=base;this.username=username;this.password=password;this.timeout=timeout;
    }
    public Mono<String> assertion(boolean required){
        return ReactiveSecurityContextHolder.getContext().flatMap(context->Mono.justOrEmpty(context.getAuthentication())).filter(auth->auth!=null && auth.isAuthenticated())
            .map(auth->auth.getCredentials()).filter(Jwt.class::isInstance).cast(Jwt.class)
            .filter(jwt->jwt.getExpiresAt()!=null && jwt.getExpiresAt().isAfter(Instant.now())).map(Jwt::getTokenValue)
            .switchIfEmpty(required?Mono.error(RecoveryFailure.refused()):Mono.empty());
    }
    public <T> Mono<T> post(String endpoint,Rpc request,Class<T> result){
        if(!java.util.Set.of("admit","journal","audit").contains(endpoint))return Mono.error(RecoveryFailure.unavailable());
        return web.post().uri(base+"/"+endpoint).headers(headers->headers.setBasicAuth(username,password))
            .contentType(MediaType.APPLICATION_JSON).bodyValue(request).retrieve()
            .onStatus(status->!status.is2xxSuccessful(),response->response.releaseBody().thenReturn(RecoveryFailure.unavailable()))
            .bodyToMono(result).timeout(timeout).switchIfEmpty(Mono.error(RecoveryFailure.unavailable()))
            .onErrorMap(error->RecoveryFailure.unavailable());
    }
    @Override public String toString(){return "NativeRecoveryHttpClientImpl[redacted]";}
}
