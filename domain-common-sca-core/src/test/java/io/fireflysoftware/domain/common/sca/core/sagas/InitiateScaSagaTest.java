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


package io.fireflysoftware.domain.common.sca.core.sagas;

import io.fireflysoftware.common.sca.sdk.api.ScaAttemptControllerApi;
import io.fireflysoftware.common.sca.sdk.api.ScaAuditControllerApi;
import io.fireflysoftware.common.sca.sdk.api.ScaChallengeControllerApi;
import io.fireflysoftware.common.sca.sdk.api.ScaOperationControllerApi;
import io.fireflysoftware.common.sca.sdk.api.ScaOperationHistoryControllerApi;
import io.fireflysoftware.common.sca.sdk.model.SCAChallengeDTO;
import io.fireflysoftware.common.sca.sdk.model.SCAOperationDTO;
import io.fireflysoftware.domain.common.sca.core.TestScaCoreApplication;
import io.fireflysoftware.domain.common.sca.core.commands.InitiateScaOperationCommand;
import io.fireflysoftware.domain.common.sca.core.sagas.InitiateScaSaga;
import org.fireflyframework.orchestration.saga.engine.SagaResult;
import org.fireflyframework.orchestration.saga.engine.StepInputs;
import org.fireflyframework.orchestration.saga.engine.SagaEngine;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = TestScaCoreApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "firefly.cache.default-cache-type=CAFFEINE")
class InitiateScaSagaTest {

    @Autowired
    private SagaEngine sagaEngine;

    @MockitoBean
    private ScaOperationControllerApi scaOperationApi;

    @MockitoBean
    private ScaChallengeControllerApi scaChallengeApi;

    // Required by other handlers in the same Spring context
    @MockitoBean
    private ScaAttemptControllerApi scaAttemptApi;

    @MockitoBean
    private ScaAuditControllerApi scaAuditApi;

    @MockitoBean
    private ScaOperationHistoryControllerApi scaHistoryApi;

    @Test
    void happyPath_createsOperationAndChallengeAndTriggers() {
        UUID operationId = UUID.randomUUID();
        UUID challengeId = UUID.randomUUID();

        SCAOperationDTO createdOperation = new SCAOperationDTO()
                .status(SCAOperationDTO.StatusEnum.PENDING);

        SCAChallengeDTO createdChallenge = new SCAChallengeDTO()
                .scaOperationId(operationId);

        when(scaOperationApi.createOperation(any(SCAOperationDTO.class), any()))
                .thenReturn(Mono.just(createdOperation));
        when(scaChallengeApi.createChallenge(any(), any(SCAChallengeDTO.class), any()))
                .thenReturn(Mono.just(createdChallenge));
        when(scaOperationApi.triggerSCA(any(), any()))
                .thenReturn(Mono.empty());

        InitiateScaOperationCommand command = new InitiateScaOperationCommand("LOGIN", UUID.randomUUID(), "MOBILE");
        StepInputs inputs = StepInputs.of(InitiateScaSaga.STEP_CREATE_OPERATION, command);

        StepVerifier.create(sagaEngine.execute(InitiateScaSaga.SAGA_NAME, inputs))
                .assertNext(result -> {
                    assertThat(result.isSuccess()).isTrue();
                    assertThat(result.steps()).containsKeys(InitiateScaSaga.STEP_CREATE_OPERATION, InitiateScaSaga.STEP_GENERATE_CHALLENGE, InitiateScaSaga.STEP_SEND_CHALLENGE);
                })
                .verifyComplete();
    }

    @Test
    void compensationTriggered_whenGenerateChallengeStepFails() {
        UUID operationId = UUID.randomUUID();

        SCAOperationDTO createdOperation = new SCAOperationDTO()
                .status(SCAOperationDTO.StatusEnum.PENDING);

        when(scaOperationApi.createOperation(any(SCAOperationDTO.class), any()))
                .thenReturn(Mono.just(createdOperation));
        when(scaChallengeApi.createChallenge(any(), any(SCAChallengeDTO.class), any()))
                .thenReturn(Mono.error(new RuntimeException("challenge service unavailable")));
        when(scaOperationApi.updateOperation(any(), any(SCAOperationDTO.class), any()))
                .thenReturn(Mono.just(new SCAOperationDTO().status(SCAOperationDTO.StatusEnum.CANCELLED)));

        InitiateScaOperationCommand command = new InitiateScaOperationCommand("TRANSFER", UUID.randomUUID(), "WEB");
        StepInputs inputs = StepInputs.of(InitiateScaSaga.STEP_CREATE_OPERATION, command);

        StepVerifier.create(sagaEngine.execute(InitiateScaSaga.SAGA_NAME, inputs))
                .assertNext(result -> {
                    assertThat(result.isFailed()).isTrue();
                    assertThat(result.compensatedSteps()).contains(InitiateScaSaga.STEP_CREATE_OPERATION);
                })
                .verifyComplete();
    }
}
