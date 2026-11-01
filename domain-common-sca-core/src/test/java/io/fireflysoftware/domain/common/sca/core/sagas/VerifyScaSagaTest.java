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

import io.fireflysoftware.common.sca.sdk.api.ScaAuditControllerApi;
import io.fireflysoftware.common.sca.sdk.api.ScaAttemptControllerApi;
import io.fireflysoftware.common.sca.sdk.api.ScaChallengeControllerApi;
import io.fireflysoftware.common.sca.sdk.api.ScaOperationControllerApi;
import io.fireflysoftware.common.sca.sdk.api.ScaOperationHistoryControllerApi;
import io.fireflysoftware.common.sca.sdk.model.SCAAuditDTO;
import io.fireflysoftware.common.sca.sdk.model.SCAAttemptDTO;
import io.fireflysoftware.common.sca.sdk.model.SCAOperationDTO;
import io.fireflysoftware.domain.common.sca.core.TestScaCoreApplication;
import io.fireflysoftware.domain.common.sca.core.commands.VerifyScaAttemptCommand;
import io.fireflysoftware.domain.common.sca.core.sagas.VerifyScaSaga;
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
class VerifyScaSagaTest {

    @Autowired
    private SagaEngine sagaEngine;

    @MockitoBean
    private ScaAttemptControllerApi scaAttemptApi;

    @MockitoBean
    private ScaOperationControllerApi scaOperationApi;

    @MockitoBean
    private ScaAuditControllerApi scaAuditApi;

    @MockitoBean
    private ScaChallengeControllerApi scaChallengeApi;

    @MockitoBean
    private ScaOperationHistoryControllerApi scaOperationHistoryApi;

    @Test
    void happyPath_recordsAttemptUpdatesStatusAndAudit() {
        UUID operationId = UUID.randomUUID();
        UUID challengeId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();

        SCAAttemptDTO attempt = new SCAAttemptDTO()
                .scaChallengeId(challengeId)
                .attemptValue("123456")
                .success(true);

        SCAOperationDTO updatedOp = new SCAOperationDTO().status(SCAOperationDTO.StatusEnum.VERIFIED);
        SCAAuditDTO audit = new SCAAuditDTO().eventType(SCAAuditDTO.EventTypeEnum.VERIFIED);

        when(scaChallengeApi.validateChallenge(any(), any(), any(), any()))
                .thenReturn(Mono.just("Challenge Validated"));
        when(scaOperationApi.getOperation(any(), any()))
                .thenReturn(Mono.just(new SCAOperationDTO()
                        .referenceId(operationId.toString())
                        .operationType(SCAOperationDTO.OperationTypeEnum.LOGIN)
                        .partyId(challengeId.toString())
                        .status(SCAOperationDTO.StatusEnum.PENDING)));
        when(scaOperationApi.updateOperation(any(), any(SCAOperationDTO.class), any()))
                .thenReturn(Mono.just(updatedOp));
        when(scaAuditApi.createAuditEvent(any(), any(SCAAuditDTO.class), any()))
                .thenReturn(Mono.just(audit));

        VerifyScaAttemptCommand command = new VerifyScaAttemptCommand(operationId, challengeId, "123456");
        StepInputs inputs = StepInputs.of(VerifyScaSaga.STEP_VALIDATE_ATTEMPT, command);

        StepVerifier.create(sagaEngine.execute(VerifyScaSaga.SAGA_NAME, inputs))
                .assertNext(result -> {
                    assertThat(result.isSuccess()).isTrue();
                    assertThat(result.steps()).containsKeys(VerifyScaSaga.STEP_VALIDATE_ATTEMPT, VerifyScaSaga.STEP_UPDATE_STATUS, VerifyScaSaga.STEP_RECORD_AUDIT);
                })
                .verifyComplete();
    }

    @Test
    void compensationTriggered_whenUpdateStatusStepFails() {
        UUID operationId = UUID.randomUUID();
        UUID challengeId = UUID.randomUUID();

        SCAAttemptDTO attempt = new SCAAttemptDTO()
                .scaChallengeId(challengeId)
                .attemptValue("000000")
                .success(false);

        when(scaChallengeApi.validateChallenge(any(), any(), any(), any()))
                .thenReturn(Mono.just("Challenge Validated"));
        when(scaOperationApi.getOperation(any(), any()))
                .thenReturn(Mono.just(new SCAOperationDTO()
                        .referenceId(operationId.toString())
                        .operationType(SCAOperationDTO.OperationTypeEnum.LOGIN)
                        .partyId(challengeId.toString())
                        .status(SCAOperationDTO.StatusEnum.PENDING)));
        when(scaOperationApi.updateOperation(any(), any(SCAOperationDTO.class), any()))
                .thenReturn(Mono.error(new RuntimeException("status update failed")));
        when(scaAttemptApi.updateAttempt(any(), any(), any(), any(SCAAttemptDTO.class), any()))
                .thenReturn(Mono.just(new SCAAttemptDTO().success(false)));

        VerifyScaAttemptCommand command = new VerifyScaAttemptCommand(operationId, challengeId, "000000");
        StepInputs inputs = StepInputs.of(VerifyScaSaga.STEP_VALIDATE_ATTEMPT, command);

        StepVerifier.create(sagaEngine.execute(VerifyScaSaga.SAGA_NAME, inputs))
                .assertNext(result -> {
                    assertThat(result.isFailed()).isTrue();
                    assertThat(result.compensatedSteps()).contains(VerifyScaSaga.STEP_VALIDATE_ATTEMPT);
                })
                .verifyComplete();
    }
}
