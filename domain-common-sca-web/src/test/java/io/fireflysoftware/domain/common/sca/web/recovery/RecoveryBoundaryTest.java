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


package io.fireflysoftware.domain.common.sca.web.recovery;

import io.fireflysoftware.domain.common.sca.core.recovery.*;
import io.fireflysoftware.domain.common.sca.web.controllers.recovery.ScaRecoveryController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.WebFilterChainProxy;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class RecoveryBoundaryTest {
    @Test void disabledBoundaryDoesNotNeedIssuerDecoderNativeCapabilityOrOrchestrator() {
        var chain=new RecoverySecurityConfiguration().disabled(ServerHttpSecurity.http());
        var beans=new StaticListableBeanFactory();
        var controller=new ScaRecoveryController(beans.getBeanProvider(RecoveryOrchestratorImpl.class),beans.getBeanProvider(RecoveryAuditPort.class));
        var client=WebTestClient.bindToController(controller).webFilter(new WebFilterChainProxy(chain)).build();
        for(String route:List.of("/api/v1/sca/recovery-operations","/api/v1/sca/operations","/actuator/health"))
            client.post().uri(route).exchange().expectStatus().isForbidden();
    }
    @Test void controllerWithoutEnabledServiceReturnsSafeUnavailable() {
        var beans=new StaticListableBeanFactory();
        WebTestClient.bindToController(new ScaRecoveryController(beans.getBeanProvider(RecoveryOrchestratorImpl.class),beans.getBeanProvider(RecoveryAuditPort.class))).build()
            .post().uri("/api/v1/sca/recovery-operations").exchange().expectStatus().isEqualTo(503).expectBody().isEmpty();
    }
    @Test void typedShapeRefusalWaitsForDurableBoundaryAudit() {
        var service=org.mockito.Mockito.mock(RecoveryOrchestratorImpl.class);
        var operation=UUID.fromString("d89c7273-40cd-4827-baf9-e22862799859");
        org.mockito.Mockito.when(service.reserve(operation,null)).thenReturn(Mono.error(new RecoveryFailure(RecoveryFailure.Kind.INVALID)));
        var events=new java.util.ArrayList<RecoveryAuditPort.Event>();
        RecoveryAuditPort audit=new RecoveryAuditPort(){public boolean isQualified(){return true;}public Mono<Void> record(Event event){events.add(event);return Mono.empty();}};
        var beans=new StaticListableBeanFactory();beans.addBean("service",service);beans.addBean("audit",audit);
        WebTestClient.bindToController(new ScaRecoveryController(beans.getBeanProvider(RecoveryOrchestratorImpl.class),beans.getBeanProvider(RecoveryAuditPort.class))).build()
            .post().uri("/api/v1/sca/recovery-operations/"+operation+"/verifications/reservations")
            .contentType(org.springframework.http.MediaType.APPLICATION_JSON).bodyValue("{\"challengeId\":null}").exchange().expectStatus().isBadRequest();
        assertThat(events).hasSize(1);assertThat(events.getFirst().actor()).isNull();assertThat(events.getFirst().outcome()).isEqualTo(RecoveryAuditPort.Outcome.REFUSED);
    }
}
