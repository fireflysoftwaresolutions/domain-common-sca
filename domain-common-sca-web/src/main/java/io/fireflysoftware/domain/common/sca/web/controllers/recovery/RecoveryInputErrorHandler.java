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


package io.fireflysoftware.domain.common.sca.web.controllers.recovery;

import io.fireflysoftware.domain.common.sca.core.recovery.RecoveryAuditPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;
import java.time.Duration;

@RestControllerAdvice(assignableTypes=ScaRecoveryController.class)
@org.springframework.core.annotation.Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE)
public class RecoveryInputErrorHandler {
    private final ObjectProvider<RecoveryAuditPort> audits;
    public RecoveryInputErrorHandler(ObjectProvider<RecoveryAuditPort> audits){this.audits=audits;}
    @ExceptionHandler(ServerWebInputException.class)
    public Mono<ResponseEntity<Void>> malformed(){
        return Mono.defer(()->{var audit=audits.getIfAvailable();if(audit==null || !audit.isQualified())return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).<Void>build());
            return Mono.defer(()->audit.record(new RecoveryAuditPort.Event(null,null,RecoveryAuditPort.Outcome.REFUSED))).timeout(Duration.ofSeconds(5))
                .thenReturn(ResponseEntity.badRequest().<Void>build());}).onErrorReturn(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build());
    }
}
