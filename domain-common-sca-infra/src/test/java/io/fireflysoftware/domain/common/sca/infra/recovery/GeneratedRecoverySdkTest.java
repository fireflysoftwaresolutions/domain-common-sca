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

import io.fireflysoftware.common.sca.sdk.api.ScaRecoveryApi;
import io.fireflysoftware.common.sca.sdk.invoker.ApiClient;
import io.fireflysoftware.common.sca.sdk.model.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.reactive.function.client.*;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import java.net.URI;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;

/** Generated binary/codec/transport qualification only; no native owner, factor or unlock acceptance is implied. */
class GeneratedRecoverySdkTest {
    @Test void actualProducerGeneratedMethodsHaveNoGenericCacheHeaderAndUseOriginalDateLibrary() throws Exception {
        var types=Map.of("createRecoveryOperation",CreateRecoveryOperationRequest.class,
            "issueRecoveryChallenge",IssueRecoveryChallengeRequest.class,"verifyRecoveryChallenge",VerifyRecoveryChallengeRequest.class,
            "reserveRecoveryVerification",ReserveRecoveryVerificationRequest.class,"completeRecoveryVerification",CompleteRecoveryVerificationRequest.class,
            "consumeRecoveryAuthorization",ConsumeRecoveryAuthorizationRequest.class,"verifyRecoveryAuthorization",VerifyRecoveryAuthorizationRequest.class,
            "recordRecoveryExecution",RecordRecoveryExecutionRequest.class);
        for(var entry:types.entrySet()) {
            var parameters=entry.getKey().equals("createRecoveryOperation")?new Class<?>[]{entry.getValue()}:new Class<?>[]{UUID.class,entry.getValue()};
            assertThat(ScaRecoveryApi.class.getMethod(entry.getKey(),parameters).getReturnType()).isEqualTo(Mono.class);
        }
        assertThat(RecoveryAuthorizationReceipt.class.getMethod("getExpiresAt").getReturnType()).isEqualTo(LocalDateTime.class);
    }
    @Test void actualGeneratedClientDecodesUtcReceiptWithoutMutableTokenState() {
        var seen=new AtomicReference<ClientRequest>();
        WebClient web=WebClient.builder().exchangeStrategies(UtcRecoveryCodecs.strategies()).filter(new ScopedRecoveryBearer(URI.create("https://core.example")))
            .exchangeFunction(request->{seen.set(request);return Mono.just(ClientResponse.create(HttpStatus.CREATED).header("Content-Type","application/json")
                .body("{\"operationId\":\"d89c7273-40cd-4827-baf9-e22862799859\",\"expiresAt\":\"2026-10-31T20:01:00Z\"}").build());}).build();
        var client=new ApiClient(web);client.setBasePath("https://core.example");
        var api=new ScaRecoveryApi(client);
        var jwt=Jwt.withTokenValue("synthetic-source-fixture").header("alg","RS256").subject("opaque")
            .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
        StepVerifier.create(api.createRecoveryOperation(new CreateRecoveryOperationRequest().idempotencyKey("source-fixture"))
            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(new JwtAuthenticationToken(jwt))))
            .assertNext(value->{assertThat(value.getOperationId()).isEqualTo(UUID.fromString("d89c7273-40cd-4827-baf9-e22862799859"));
                assertThat(value.getExpiresAt()).isEqualTo(LocalDateTime.of(2026,10,31,20,1));}).verifyComplete();
        assertThat(seen.get().url().getPath()).isEqualTo("/api/v1/sca/recovery-operations");
        assertThat(seen.get().headers().getFirst("Authorization")).isEqualTo("Bearer synthetic-source-fixture");
        assertThat(seen.get().headers().headerNames()).doesNotContain("X-Idempotency-Key");
    }
    @Test void actualGeneratedReceiptVerificationRequestUsesRfc3339UtcForCoreInstant() throws Exception {
        var request=new VerifyRecoveryAuthorizationRequest().executionId(UUID.fromString("c10eceaa-f26b-4964-bd76-93c504250a8e"))
            .expiresAt(LocalDateTime.of(2026,10,31,20,1));
        String json=UtcRecoveryCodecs.mapper().writeValueAsString(request);
        assertThat(json).contains("\"expiresAt\":\"2026-10-31T20:01:00Z\"");
        var decoded=UtcRecoveryCodecs.mapper().readValue("{\"expiresAt\":\"2026-10-31T21:01:00+01:00\"}",VerifyRecoveryAuthorizationRequest.class);
        assertThat(decoded.getExpiresAt()).isEqualTo(LocalDateTime.of(2026,10,31,20,1));
    }
}
