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

import io.fireflysoftware.domain.common.sca.core.recovery.*;
import org.fireflyframework.web.idempotency.annotation.DisableIdempotency;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.security.*;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import reactor.core.publisher.Mono;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Function;

@RestController
@RequestMapping("/api/v1/sca/recovery-operations")
@io.swagger.v3.oas.annotations.tags.Tag(name="SCA Recovery")
@SecurityScheme(name="bearerAuth",type=SecuritySchemeType.HTTP,scheme="bearer",bearerFormat="JWT")
@SecurityRequirement(name="bearerAuth")
@ApiResponses({@ApiResponse(responseCode="401",description="Verified restricted recovery assertion required"),
    @ApiResponse(responseCode="403",description="Recovery refused"),@ApiResponse(responseCode="503",description="Recovery unavailable")})
public class ScaRecoveryController {
    private final ObjectProvider<RecoveryOrchestratorImpl> services;
    private final ObjectProvider<RecoveryAuditPort> audits;
    public ScaRecoveryController(ObjectProvider<RecoveryOrchestratorImpl> services,ObjectProvider<RecoveryAuditPort> audits){this.services=services;this.audits=audits;}
    public record ChallengeRequest(UUID challengeId) { }
    public record CompletionRequest(UUID challengeId,UUID reservationId,String attestation) {
        @Override public String toString(){return "CompletionRequest[redacted]";}
    }
    public record AuthorizationRequest(UUID executionId,Instant expiresAt) { }
    public record ExecutionRequest(UUID executionId,Instant expiresAt,String outcome) { }
    public record Acknowledgement(UUID operationId,String status) { }
    @PostMapping @DisableIdempotency @Operation(operationId="beginRecovery") @ApiResponse(responseCode="201",description="Bound recovery operation and challenge references",content=@Content(schema=@Schema(implementation=RecoveryStart.class)))
    public Mono<ResponseEntity<RecoveryStart>> begin(){return respond(service->service.begin(),HttpStatus.CREATED);}
    @PostMapping("/{operationId}/verifications/reservations") @DisableIdempotency @Operation(operationId="reserveRecoveryVerification") @ApiResponse(responseCode="201",description="Committed verification reservation",content=@Content(schema=@Schema(implementation=RecoveryReservation.class)))
    public Mono<ResponseEntity<RecoveryReservation>> reserve(@PathVariable UUID operationId,@RequestBody ChallengeRequest request){
        return respond(service->service.reserve(operationId,request==null?null:request.challengeId()),HttpStatus.CREATED);
    }
    @PostMapping("/{operationId}/verifications/complete") @DisableIdempotency @Operation(operationId="completeRecoveryVerification") @ApiResponse(responseCode="202",description="Generic acknowledgement only",content=@Content(schema=@Schema(implementation=Acknowledgement.class)))
    public Mono<ResponseEntity<Acknowledgement>> complete(@PathVariable UUID operationId,@RequestBody CompletionRequest request){
        return respond(service->request==null?Mono.error(new RecoveryFailure(RecoveryFailure.Kind.INVALID)):
            service.complete(operationId,request.challengeId(),request.reservationId(),request.attestation()).thenReturn(new Acknowledgement(operationId,"ACKNOWLEDGED")),HttpStatus.ACCEPTED);
    }
    @PostMapping("/{operationId}/authorizations") @DisableIdempotency @Operation(operationId="consumeRecoveryAuthorization") @ApiResponse(responseCode="200",description="Bound authorization reference; native execution is separate",content=@Content(schema=@Schema(implementation=RecoveryAuthorization.class)))
    public Mono<ResponseEntity<RecoveryAuthorization>> consume(@PathVariable UUID operationId){return respond(service->service.consume(operationId),HttpStatus.OK);}
    @PostMapping("/{operationId}/authorizations/verify") @DisableIdempotency @Operation(operationId="verifyRecoveryAuthorization") @ApiResponse(responseCode="200",description="Online verified canonical authorization",content=@Content(schema=@Schema(implementation=RecoveryAuthorization.class)))
    public Mono<ResponseEntity<RecoveryAuthorization>> verify(@PathVariable UUID operationId,@RequestBody AuthorizationRequest request){
        return respond(service->request==null?Mono.error(new RecoveryFailure(RecoveryFailure.Kind.INVALID)):
            service.verify(operationId,request.executionId(),request.expiresAt()),HttpStatus.OK);
    }
    @PostMapping("/{operationId}/executions") @DisableIdempotency @Operation(operationId="recordRecoveryExecution") @ApiResponse(responseCode="204",description="Authoritative native journal outcome reconciled",content=@Content)
    public Mono<ResponseEntity<Void>> record(@PathVariable UUID operationId,@RequestBody ExecutionRequest request){
        return respond(service->request==null?Mono.error(new RecoveryFailure(RecoveryFailure.Kind.INVALID)):
            service.record(operationId,new RecoveryAuthorization(operationId,request.executionId(),null,request.expiresAt()),request.outcome()),HttpStatus.NO_CONTENT);
    }
    private <T> Mono<ResponseEntity<T>> respond(Function<RecoveryOrchestratorImpl,Mono<T>> action,HttpStatus status){
        return Mono.defer(()->{var service=services.getIfAvailable();return service==null?Mono.<T>error(RecoveryFailure.unavailable()):action.apply(service);})
            .map(value->ResponseEntity.status(status).body(value)).defaultIfEmpty(ResponseEntity.status(status).build())
            .onErrorResume(error->{HttpStatus response=HttpStatus.SERVICE_UNAVAILABLE;
                if(error instanceof RecoveryFailure failure)response=switch(failure.kind()){
                    case UNAUTHENTICATED->HttpStatus.UNAUTHORIZED;case REFUSED->HttpStatus.FORBIDDEN;case INVALID->HttpStatus.BAD_REQUEST;case UNAVAILABLE->HttpStatus.SERVICE_UNAVAILABLE;};
                HttpStatus selected=response;
                return Mono.defer(()->{var audit=audits.getIfAvailable();
                    if(audit==null || !audit.isQualified())return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).<T>build());
                    return Mono.defer(()->audit.record(new RecoveryAuditPort.Event(null,null,RecoveryAuditPort.Outcome.REFUSED)))
                        .timeout(java.time.Duration.ofSeconds(5)).thenReturn(ResponseEntity.status(selected).<T>build());
                }).onErrorReturn(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build());});
    }
}
