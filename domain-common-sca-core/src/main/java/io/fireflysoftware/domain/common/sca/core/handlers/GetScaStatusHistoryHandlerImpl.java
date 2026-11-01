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

import io.fireflysoftware.common.sca.sdk.api.ScaOperationHistoryControllerApi;
import io.fireflysoftware.common.sca.sdk.model.PaginationResponseSCAOperationHistoryDTO;
import io.fireflysoftware.domain.common.sca.core.queries.GetScaStatusHistoryQuery;
import lombok.extern.slf4j.Slf4j;
import org.fireflyframework.cqrs.annotations.QueryHandlerComponent;
import org.fireflyframework.cqrs.query.QueryHandler;
import reactor.core.publisher.Mono;
import java.util.UUID;

/**
 * Handles {@link GetScaStatusHistoryQuery} by fetching the operation status-change history.
 */
@Slf4j
@QueryHandlerComponent(cacheable = true, cacheTtl = 60, metrics = true)
public class GetScaStatusHistoryHandlerImpl extends QueryHandler<GetScaStatusHistoryQuery, PaginationResponseSCAOperationHistoryDTO> {

    private final ScaOperationHistoryControllerApi scaHistoryApi;

    public GetScaStatusHistoryHandlerImpl(ScaOperationHistoryControllerApi scaHistoryApi) {
        this.scaHistoryApi = scaHistoryApi;
    }

    @Override
    protected Mono<PaginationResponseSCAOperationHistoryDTO> doHandle(GetScaStatusHistoryQuery query) {
        log.debug("Fetching status history for operationId={}", query.getOperationId());

        return scaHistoryApi.getAllHistory(query.getOperationId(), 0, 20, "eventTime", "DESC", UUID.randomUUID().toString());
    }
}
