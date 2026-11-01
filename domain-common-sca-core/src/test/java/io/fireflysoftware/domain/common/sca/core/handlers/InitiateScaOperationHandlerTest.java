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
import org.fireflyframework.orchestration.saga.engine.SagaResult;
import org.fireflyframework.orchestration.saga.engine.StepInputs;
import org.fireflyframework.orchestration.saga.engine.SagaEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InitiateScaOperationHandlerTest {

    @Mock
    private SagaEngine sagaEngine;

    private InitiateScaOperationHandlerImpl handler;

    @BeforeEach
    void setUp() {
        handler = new InitiateScaOperationHandlerImpl(sagaEngine);
    }

    @Test
    void doHandle_dispatchesInitiateScaSagaWithCorrectStepInput() {
        UUID resourceId = UUID.randomUUID();
        InitiateScaOperationCommand command = new InitiateScaOperationCommand("LOGIN", resourceId, "MOBILE");

        SagaResult expectedResult = mock(SagaResult.class);
        when(sagaEngine.execute(eq(InitiateScaSagaImpl.SAGA_NAME), any(StepInputs.class)))
                .thenReturn(Mono.just(expectedResult));

        StepVerifier.create(handler.doHandle(command))
                .expectNext(expectedResult)
                .verifyComplete();

        ArgumentCaptor<StepInputs> captor = ArgumentCaptor.forClass(StepInputs.class);
        verify(sagaEngine).execute(eq(InitiateScaSagaImpl.SAGA_NAME), captor.capture());
        assertThat(captor.getValue()).isNotNull();
    }

    @Test
    void doHandle_propagatesErrorFromSagaEngine() {
        UUID resourceId = UUID.randomUUID();
        InitiateScaOperationCommand command = new InitiateScaOperationCommand("TRANSFER", resourceId, "WEB");

        when(sagaEngine.execute(eq(InitiateScaSagaImpl.SAGA_NAME), any(StepInputs.class)))
                .thenReturn(Mono.error(new RuntimeException("saga failure")));

        StepVerifier.create(handler.doHandle(command))
                .expectErrorMessage("saga failure")
                .verify();
    }
}
