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

import io.fireflysoftware.common.sca.sdk.api.ScaChallengeControllerApi;
import io.fireflysoftware.common.sca.sdk.api.ScaOperationControllerApi;
import io.fireflysoftware.common.sca.sdk.model.SCAChallengeDTO;
import io.fireflysoftware.common.sca.sdk.model.SCAOperationDTO;
import io.fireflysoftware.domain.common.sca.core.commands.InitiateScaOperationCommand;
import lombok.extern.slf4j.Slf4j;
import org.fireflyframework.orchestration.core.argument.Input;
import org.fireflyframework.orchestration.core.argument.SetVariable;
import org.fireflyframework.orchestration.core.argument.Variable;
import org.fireflyframework.orchestration.saga.annotation.Saga;
import org.fireflyframework.orchestration.saga.annotation.SagaStep;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import java.util.UUID;

/**
 * Orchestrates the full SCA initiation flow: create operation → generate challenge → send challenge.
 * On failure, compensates by cancelling the operation.
 */
@Slf4j
@Component
@Saga(name = "initiate-sca")
public class InitiateScaSagaImpl {

    /** Name of this saga as registered with the SagaEngine. */
    public static final String SAGA_NAME = "initiate-sca";

    /** Step ID for the create-operation step (also used as the initial StepInputs key). */
    public static final String STEP_CREATE_OPERATION = "createScaOperation";

    /** Step ID for the generate-challenge step. */
    public static final String STEP_GENERATE_CHALLENGE = "generateChallenge";

    /** Step ID for the send-challenge step. */
    public static final String STEP_SEND_CHALLENGE = "sendChallenge";

    static final String CTX_OPERATION_ID = "CTX_OPERATION_ID";
    static final String CTX_CHALLENGE_ID = "CTX_CHALLENGE_ID";

    private final ScaOperationControllerApi scaOperationApi;
    private final ScaChallengeControllerApi scaChallengeApi;

    public InitiateScaSagaImpl(ScaOperationControllerApi scaOperationApi,
                           ScaChallengeControllerApi scaChallengeApi) {
        this.scaOperationApi = scaOperationApi;
        this.scaChallengeApi = scaChallengeApi;
    }

    @SagaStep(id = STEP_CREATE_OPERATION, compensate = "compensateCreateOperation", retry = 3, backoffMs = 500)
    @SetVariable(CTX_OPERATION_ID)
    public Mono<SCAOperationDTO> createScaOperation(@Input InitiateScaOperationCommand command) {
        log.debug("Creating SCA operation for resourceId={}", command.getResourceId());

        SCAOperationDTO dto = new SCAOperationDTO()
                .operationType(SCAOperationDTO.OperationTypeEnum.fromValue(command.getOperationType()))
                .partyId(command.getResourceId().toString())
                .referenceId(command.getChannel())
                .status(SCAOperationDTO.StatusEnum.PENDING);

        return scaOperationApi.createOperation(dto, UUID.randomUUID().toString());
    }

    @SagaStep(id = STEP_GENERATE_CHALLENGE, dependsOn = STEP_CREATE_OPERATION, retry = 2)
    @SetVariable(CTX_CHALLENGE_ID)
    public Mono<SCAChallengeDTO> generateChallenge(@Variable(CTX_OPERATION_ID) SCAOperationDTO operation) {
        log.debug("Generating challenge for operationId={}", operation.getId());

        SCAChallengeDTO dto = new SCAChallengeDTO()
                .scaOperationId(operation.getId());

        return scaChallengeApi.createChallenge(operation.getId(), dto, UUID.randomUUID().toString());
    }

    @SagaStep(id = STEP_SEND_CHALLENGE, dependsOn = STEP_GENERATE_CHALLENGE)
    public Mono<String> sendChallenge(@Variable(CTX_OPERATION_ID) SCAOperationDTO operation) {
        log.debug("Triggering SCA challenge delivery for operationId={}", operation.getId());

        return scaOperationApi.triggerSCA(operation.getId(), UUID.randomUUID().toString())
                .thenReturn("triggered");
    }

    /**
     * Compensation: cancel the created operation if any downstream step fails.
     */
    public Mono<Void> compensateCreateOperation(@Variable(CTX_OPERATION_ID) SCAOperationDTO operation) {
        log.warn("Compensating: cancelling SCA operationId={}", operation.getId());

        SCAOperationDTO cancelDto = new SCAOperationDTO()
                .status(SCAOperationDTO.StatusEnum.CANCELLED);

        return scaOperationApi.updateOperation(operation.getId(), cancelDto, UUID.randomUUID().toString())
                .then();
    }
}
