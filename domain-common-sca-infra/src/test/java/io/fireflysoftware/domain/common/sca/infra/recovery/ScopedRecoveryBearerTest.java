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


package io.fireflysoftware.domain.common.sca.infra.recovery;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.reactive.function.client.*;
import org.springframework.http.*;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import java.net.URI;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import static org.assertj.core.api.Assertions.*;

class ScopedRecoveryBearerTest {
    @Test void concurrentSubscriptionsRelayOnlyTheirOwnValidatedContextAndCannotOverrideDestinationOrHeader() {
        var seen=new CopyOnWriteArrayList<String>();
        WebClient client=WebClient.builder().filter(new ScopedRecoveryBearer(URI.create("https://core.example")))
            .exchangeFunction(request->{seen.add(request.headers().getFirst("Authorization"));return Mono.just(ClientResponse.create(HttpStatus.OK).body("ok").build());}).build();
        var a=client.post().uri("https://core.example/api/v1/sca/recovery-operations").retrieve().bodyToMono(String.class)
            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication("synthetic-token-a")));
        var b=client.post().uri("https://core.example/api/v1/sca/recovery-operations").retrieve().bodyToMono(String.class)
            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication("synthetic-token-b")));
        StepVerifier.create(Mono.zip(a,b)).expectNextCount(1).verifyComplete();
        assertThat(seen).containsExactlyInAnyOrder("Bearer synthetic-token-a","Bearer synthetic-token-b");
        StepVerifier.create(client.get().uri("https://other.example/api/v1/sca/recovery-operations").retrieve().bodyToMono(String.class)
            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication("synthetic-token-a")))).expectError().verify();
        StepVerifier.create(client.post().uri("https://core.example/api/v1/sca/recovery-operations").header("Authorization","Bearer override")
            .retrieve().bodyToMono(String.class).contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication("synthetic-token-a")))).expectError().verify();
        assertThat(seen).hasSize(2);
    }
    @Test void absentCredentialsCannotSilentlyBecomeAnonymousSdkRequest() {
        WebClient client=WebClient.builder().filter(new ScopedRecoveryBearer(URI.create("https://core.example")))
            .exchangeFunction(request->Mono.error(new AssertionError("Downstream must not be called"))).build();
        StepVerifier.create(client.post().uri("https://core.example/api/v1/sca/recovery-operations").retrieve().bodyToMono(String.class)).expectError().verify();
    }
    private static JwtAuthenticationToken authentication(String value) {
        return new JwtAuthenticationToken(Jwt.withTokenValue(value).header("alg","RS256").subject("opaque")
            .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build());
    }
}
