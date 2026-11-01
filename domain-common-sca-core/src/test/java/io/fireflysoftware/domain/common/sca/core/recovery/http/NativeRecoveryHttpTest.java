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
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.web.reactive.function.client.*;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import java.net.URI;
import java.time.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;

/** Exercises transport refusals/committed receipt handling; does not qualify a real native audit sink. */
class NativeRecoveryHttpTest {
    @Test void anonymousAuditRequiresActualCommittedReceiptAndNeverNeedsActorHeaders() {
        var seen=new AtomicReference<ClientRequest>();
        var web=WebClient.builder().exchangeFunction(request->{seen.set(request);return Mono.just(ClientResponse.create(HttpStatus.OK)
            .header("Content-Type","application/json").body("{\"auditId\":\"c10eceaa-f26b-4964-bd76-93c504250a8e\",\"committedAt\":\""+Instant.now()+"\"}").build());}).build();
        var http=new NativeRecoveryHttpClientImpl(web,URI.create("https://identity.example/realms/customers/firefly-recovery/rpc"),"configured-workload","synthetic-workload-secret-not-production",Duration.ofSeconds(2));
        StepVerifier.create(new RecoveryAuditHttpPortImpl(http).record(new RecoveryAuditPort.Event(null,null,RecoveryAuditPort.Outcome.REFUSED))).verifyComplete();
        assertThat(seen.get().url().getPath()).endsWith("/rpc/audit");
        assertThat(seen.get().headers().getFirst("Authorization")).startsWith("Basic ");
        assertThat(seen.get().headers().headerNames()).doesNotContain("X-Actor-Assertion");
    }
    @Test void noReceiptOrProviderErrorCannotSatisfyDurableAuditAndDoesNotExposeResponseBody() {
        for(String response:new String[]{"{}","{\"auditId\":null,\"committedAt\":null}"}) {
            var web=WebClient.builder().exchangeFunction(request->Mono.just(ClientResponse.create(HttpStatus.OK).header("Content-Type","application/json").body(response).build())).build();
            var http=new NativeRecoveryHttpClientImpl(web,URI.create("https://identity.example/rpc"),"workload","synthetic",Duration.ofSeconds(2));
            StepVerifier.create(new RecoveryAuditHttpPortImpl(http).record(new RecoveryAuditPort.Event(null,null,RecoveryAuditPort.Outcome.REFUSED))).expectError(RecoveryFailure.class).verify();
        }
        var web=WebClient.builder().exchangeFunction(request->Mono.just(ClientResponse.create(HttpStatus.INTERNAL_SERVER_ERROR).body("private-provider-secret").build())).build();
        var http=new NativeRecoveryHttpClientImpl(web,URI.create("https://identity.example/rpc"),"workload","synthetic",Duration.ofSeconds(2));
        StepVerifier.create(http.post("audit",new NativeRecoveryHttpClientImpl.Rpc(null,null,null,null,null,null,null,null,null,null,"DOMAIN_BOUNDARY_REFUSED"),NativeRecoveryHttpClientImpl.AuditReceipt.class))
            .expectErrorMatches(error->error instanceof RecoveryFailure && !error.toString().contains("private-provider")).verify();
    }
    @Test void missingValidatedActorNeverCallsNativeAdmission() {
        var web=WebClient.builder().exchangeFunction(request->Mono.error(new AssertionError("Must not call provider"))).build();
        var http=new NativeRecoveryHttpClientImpl(web,URI.create("https://identity.example/rpc"),"workload","synthetic",Duration.ofSeconds(2));
        StepVerifier.create(http.assertion(true)).expectError(RecoveryFailure.class).verify();
    }
}
