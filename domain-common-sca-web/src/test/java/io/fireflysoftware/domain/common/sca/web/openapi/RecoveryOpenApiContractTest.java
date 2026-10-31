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


package io.fireflysoftware.domain.common.sca.web.openapi;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(classes=OpenApiGenApplication.class,webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties="spring.config.name=openapi-contract-fixture")
@ActiveProfiles("openapi-gen")
class RecoveryOpenApiContractTest {
    @Autowired WebTestClient client;
    @Test void originalProducerExposesRestrictedBearerRecoveryWithoutCredentialFieldsOrCacheHeaders() {
        client.get().uri("/v3/api-docs").exchange().expectStatus().isOk().expectBody()
            .jsonPath("$.components.securitySchemes.bearerAuth.scheme").isEqualTo("bearer")
            .jsonPath("$.paths['/api/v1/sca/recovery-operations'].post.operationId").isEqualTo("beginRecovery")
            .jsonPath("$.paths['/api/v1/sca/recovery-operations'].post.responses['201'].content").isNotEmpty()
            .jsonPath("$.components.schemas.RecoveryStart.properties.operationId").exists()
            .jsonPath("$.paths['/api/v1/sca/recovery-operations/{operationId}/verifications/reservations'].post.responses['201']").exists()
            .jsonPath("$.paths['/api/v1/sca/recovery-operations/{operationId}/verifications/complete'].post.responses['202']").exists()
            .jsonPath("$.paths['/api/v1/sca/recovery-operations/{operationId}/executions'].post.responses['204']").exists()
            .jsonPath("$.components.schemas.CompletionRequest.properties.password").doesNotExist()
            .jsonPath("$.components.schemas.CompletionRequest.properties.otp").doesNotExist()
            .jsonPath("$.components.schemas.Acknowledgement.properties.accepted").doesNotExist();
    }
}
