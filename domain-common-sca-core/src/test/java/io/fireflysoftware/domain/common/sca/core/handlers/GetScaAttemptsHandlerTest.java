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

import io.fireflysoftware.common.sca.sdk.api.ScaAttemptControllerApi;
import io.fireflysoftware.common.sca.sdk.api.ScaChallengeControllerApi;
import io.fireflysoftware.common.sca.sdk.model.PaginationResponseSCAAttemptDTO;
import io.fireflysoftware.common.sca.sdk.model.PaginationResponseSCAChallengeDTO;
import io.fireflysoftware.common.sca.sdk.model.SCAChallengeDTO;
import io.fireflysoftware.domain.common.sca.core.queries.GetScaAttemptsQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetScaAttemptsHandlerTest {

    @Mock
    private ScaChallengeControllerApi scaChallengeApi;

    @Mock
    private ScaAttemptControllerApi scaAttemptApi;

    private GetScaAttemptsHandlerImpl handler;

    @BeforeEach
    void setUp() {
        handler = new GetScaAttemptsHandlerImpl(scaChallengeApi, scaAttemptApi);
    }

    @Test
    void doHandle_returnAttemptsForLatestChallenge() {
        UUID operationId = UUID.randomUUID();
        UUID challengeId = UUID.randomUUID();
        GetScaAttemptsQuery query = new GetScaAttemptsQuery(operationId);

        SCAChallengeDTO challenge = new SCAChallengeDTO().scaOperationId(operationId);
        PaginationResponseSCAChallengeDTO challengesPage = new PaginationResponseSCAChallengeDTO()
                .content(List.of(challenge));

        PaginationResponseSCAAttemptDTO attemptsPage = new PaginationResponseSCAAttemptDTO();

        when(scaChallengeApi.getAllChallenges(eq(operationId), eq(0), eq(1), eq("createdAt"), eq("DESC"), any()))
                .thenReturn(Mono.just(challengesPage));
        when(scaAttemptApi.getAllAttempts(eq(operationId), any(), eq(0), eq(10), isNull(), isNull(), any()))
                .thenReturn(Mono.just(attemptsPage));

        StepVerifier.create(handler.doHandle(query))
                .expectNext(attemptsPage)
                .verifyComplete();
    }

    @Test
    void doHandle_returnsEmptyPageWhenNoChallengesExist() {
        UUID operationId = UUID.randomUUID();
        GetScaAttemptsQuery query = new GetScaAttemptsQuery(operationId);

        PaginationResponseSCAChallengeDTO emptyChallenges = new PaginationResponseSCAChallengeDTO()
                .content(List.of());

        when(scaChallengeApi.getAllChallenges(eq(operationId), eq(0), eq(1), eq("createdAt"), eq("DESC"), any()))
                .thenReturn(Mono.just(emptyChallenges));

        StepVerifier.create(handler.doHandle(query))
                .expectNextMatches(page -> page.getContent() == null || page.getContent().isEmpty())
                .verifyComplete();
    }
}
