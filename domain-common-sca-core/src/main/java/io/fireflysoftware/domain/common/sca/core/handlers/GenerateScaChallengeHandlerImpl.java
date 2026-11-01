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
import io.fireflysoftware.domain.common.sca.core.commands.GenerateScaChallengeCommand;
import lombok.extern.slf4j.Slf4j;
import org.fireflyframework.cqrs.annotations.CommandHandlerComponent;
import org.fireflyframework.cqrs.command.CommandHandler;
import reactor.core.publisher.Mono;

/**
 * Handles {@link GenerateScaChallengeCommand} by creating a new challenge via the SCA management API.
 * Used to (re)generate a challenge for an existing operation independently of the initiation saga.
 */
@Slf4j
@CommandHandlerComponent(timeout = 15000, metrics = true, tracing = true)
public class GenerateScaChallengeHandlerImpl extends CommandHandler<GenerateScaChallengeCommand, SCAChallengeDTO> {

    private final ScaChallengeControllerApi scaChallengeApi;

    public GenerateScaChallengeHandlerImpl(ScaChallengeControllerApi scaChallengeApi) {
        this.scaChallengeApi = scaChallengeApi;
    }

    @Override
    protected Mono<SCAChallengeDTO> doHandle(GenerateScaChallengeCommand command) {
        log.debug("Generating challenge for operationId={} type={}", command.getOperationId(), command.getChallengeType());

        SCAChallengeDTO dto = new SCAChallengeDTO()
                .scaOperationId(command.getOperationId())
                .challengeCode(Integer.toString(100000 + java.util.concurrent.ThreadLocalRandom.current().nextInt(900000)))
                .expiresAt(java.time.LocalDateTime.now().plusMinutes(5).withNano(0))
                .used(false);

        return scaChallengeApi.createChallenge(
                command.getOperationId(),
                dto,
                null
        );
    }
}
