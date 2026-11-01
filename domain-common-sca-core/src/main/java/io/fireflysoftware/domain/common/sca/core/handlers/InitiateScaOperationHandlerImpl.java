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

import io.fireflysoftware.domain.common.sca.core.commands.InitiateScaOperationCommand;
import io.fireflysoftware.domain.common.sca.core.sagas.InitiateScaSagaImpl;
import lombok.extern.slf4j.Slf4j;
import org.fireflyframework.cqrs.annotations.CommandHandlerComponent;
import org.fireflyframework.cqrs.command.CommandHandler;
import org.fireflyframework.orchestration.saga.engine.SagaResult;
import org.fireflyframework.orchestration.saga.engine.StepInputs;
import org.fireflyframework.orchestration.saga.engine.SagaEngine;
import reactor.core.publisher.Mono;

/**
 * Handles {@link InitiateScaOperationCommand} by dispatching the {@code initiate-sca} saga.
 */
@Slf4j
@CommandHandlerComponent(timeout = 30000, metrics = true, tracing = true)
public class InitiateScaOperationHandlerImpl extends CommandHandler<InitiateScaOperationCommand, SagaResult> {

    private final SagaEngine sagaEngine;

    public InitiateScaOperationHandlerImpl(SagaEngine sagaEngine) {
        this.sagaEngine = sagaEngine;
    }

    @Override
    protected Mono<SagaResult> doHandle(InitiateScaOperationCommand command) {
        log.debug("Dispatching initiate-sca saga for resourceId={}", command.getResourceId());

        StepInputs inputs = StepInputs.of(InitiateScaSagaImpl.STEP_CREATE_OPERATION, command);
        return sagaEngine.execute(InitiateScaSagaImpl.SAGA_NAME, inputs);
    }
}
