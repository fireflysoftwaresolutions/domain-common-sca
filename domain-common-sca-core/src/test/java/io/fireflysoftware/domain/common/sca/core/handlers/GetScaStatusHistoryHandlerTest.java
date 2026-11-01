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
class GetScaStatusHistoryHandlerTest {

    @Mock
    private ScaOperationHistoryControllerApi scaHistoryApi;

    private GetScaStatusHistoryHandlerImpl handler;

    @BeforeEach
    void setUp() {
        handler = new GetScaStatusHistoryHandlerImpl(scaHistoryApi);
    }

    @Test
    void doHandle_returnsHistoryPage() {
        UUID operationId = UUID.randomUUID();
        GetScaStatusHistoryQuery query = new GetScaStatusHistoryQuery(operationId);

        PaginationResponseSCAOperationHistoryDTO historyPage = new PaginationResponseSCAOperationHistoryDTO()
                .totalElements(2L);

        when(scaHistoryApi.getAllHistory(eq(operationId), eq(0), eq(20), eq("eventTime"), eq("DESC"), any()))
                .thenReturn(Mono.just(historyPage));

        StepVerifier.create(handler.doHandle(query))
                .expectNext(historyPage)
                .verifyComplete();
    }

    @Test
    void doHandle_propagatesErrorFromApi() {
        UUID operationId = UUID.randomUUID();
        GetScaStatusHistoryQuery query = new GetScaStatusHistoryQuery(operationId);

        when(scaHistoryApi.getAllHistory(eq(operationId), eq(0), eq(20), eq("eventTime"), eq("DESC"), any()))
                .thenReturn(Mono.error(new RuntimeException("history unavailable")));

        StepVerifier.create(handler.doHandle(query))
                .expectErrorMessage("history unavailable")
                .verify();
    }
}
