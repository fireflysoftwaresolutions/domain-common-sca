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

import io.fireflysoftware.common.sca.sdk.api.ScaOperationControllerApi;
import io.fireflysoftware.common.sca.sdk.model.SCAOperationDTO;
import io.fireflysoftware.domain.common.sca.core.queries.GetScaOperationQuery;
import lombok.extern.slf4j.Slf4j;
import org.fireflyframework.cqrs.annotations.QueryHandlerComponent;
import org.fireflyframework.cqrs.query.QueryHandler;
import reactor.core.publisher.Mono;
import java.util.UUID;

/**
 * Handles {@link GetScaOperationQuery} by fetching the operation from the SCA management API.
 */
@Slf4j
@QueryHandlerComponent(cacheable = true, cacheTtl = 30, metrics = true)
public class GetScaOperationHandlerImpl extends QueryHandler<GetScaOperationQuery, SCAOperationDTO> {

    private final ScaOperationControllerApi scaOperationApi;

    public GetScaOperationHandlerImpl(ScaOperationControllerApi scaOperationApi) {
        this.scaOperationApi = scaOperationApi;
    }

    @Override
    protected Mono<SCAOperationDTO> doHandle(GetScaOperationQuery query) {
        log.debug("Fetching SCA operation operationId={}", query.getOperationId());

        return scaOperationApi.getOperation(query.getOperationId(), UUID.randomUUID().toString());
    }
}
