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


package io.fireflysoftware.domain.common.sca.core.handlers;

import io.fireflysoftware.common.sca.sdk.api.ScaChallengeControllerApi;
import io.fireflysoftware.common.sca.sdk.model.SCAChallengeDTO;
import io.fireflysoftware.domain.common.sca.core.commands.ChallengeType;
import io.fireflysoftware.domain.common.sca.core.commands.GenerateScaChallengeCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GenerateScaChallengeHandlerTest {

    @Mock
    private ScaChallengeControllerApi scaChallengeApi;

    private GenerateScaChallengeHandlerImpl handler;

    @BeforeEach
    void setUp() {
        handler = new GenerateScaChallengeHandlerImpl(scaChallengeApi);
    }

    @Test
    void doHandle_createsChallenge_andReturnsDto() {
        UUID operationId = UUID.randomUUID();
        GenerateScaChallengeCommand command = new GenerateScaChallengeCommand(operationId, ChallengeType.OTP);

        SCAChallengeDTO expectedChallenge = new SCAChallengeDTO().scaOperationId(operationId);
        when(scaChallengeApi.createChallenge(eq(operationId), any(SCAChallengeDTO.class), any()))
                .thenReturn(Mono.just(expectedChallenge));

        StepVerifier.create(handler.doHandle(command))
                .expectNext(expectedChallenge)
                .verifyComplete();
    }

    @Test
    void doHandle_propagatesErrorFromApi() {
        UUID operationId = UUID.randomUUID();
        GenerateScaChallengeCommand command = new GenerateScaChallengeCommand(operationId, ChallengeType.PUSH);

        when(scaChallengeApi.createChallenge(eq(operationId), any(SCAChallengeDTO.class), any()))
                .thenReturn(Mono.error(new RuntimeException("api error")));

        StepVerifier.create(handler.doHandle(command))
                .expectErrorMessage("api error")
                .verify();
    }
}
