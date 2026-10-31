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


package io.fireflysoftware.domain.common.sca.core.recovery;

import io.fireflysoftware.common.sca.sdk.api.ScaRecoveryApi;
import io.fireflysoftware.common.sca.sdk.model.*;
import reactor.core.publisher.Mono;
import java.time.*;
import java.util.*;
import java.util.function.Function;

/** Owns no proof store or native mutation. Every downstream operation refreshes actor/current-native/PDP admission. */
public final class RecoveryOrchestrator {
    private final ScaRecoveryApi api;
    private final RecoveryAdmission admission;
    private final NativeRecoveryPort nativePort;
    private final Clock clock;
    public RecoveryOrchestrator(ScaRecoveryApi api,RecoveryAdmission admission,NativeRecoveryPort nativePort,Clock clock) {
        this.api=Objects.requireNonNull(api);this.admission=Objects.requireNonNull(admission);this.nativePort=Objects.requireNonNull(nativePort);this.clock=Objects.requireNonNull(clock);
    }
    public Mono<RecoveryStart> begin() {
        return call(RecoveryAction.BEGIN,null,first->api.createRecoveryOperation(new CreateRecoveryOperationRequest()
            .binding(first.context().coreBinding()).idempotencyKey(first.context().ceremonyId()+":create"))
            .switchIfEmpty(Mono.error(RecoveryFailure.unavailable())).flatMap(operation->{
                if(operation.getOperationId()==null || !instant(operation.getExpiresAt()).isAfter(clock.instant())) throw RecoveryFailure.unavailable();
                UUID id=operation.getOperationId();
                return issue(id,first.context(),RecoveryFactorCategory.KNOWLEDGE).flatMap(knowledge->
                    issue(id,first.context(),RecoveryFactorCategory.POSSESSION).map(possession->new RecoveryStart(id,knowledge,possession)));
            }));
    }
    private Mono<UUID> issue(UUID operation,NativeRecoveryContext expected,RecoveryFactorCategory category) {
        return call(RecoveryAction.ISSUE,operation,caller->{
            sameContext(expected,caller.context());
            return api.issueRecoveryChallenge(operation,new IssueRecoveryChallengeRequest().binding(caller.context().coreBinding()).category(category))
                .switchIfEmpty(Mono.error(RecoveryFailure.unavailable())).map(challenge->{
                    if(challenge.getChallengeId()==null || challenge.getCategory()!=category || !instant(challenge.getExpiresAt()).isAfter(clock.instant())) throw RecoveryFailure.unavailable();
                    return challenge.getChallengeId();
                });
        });
    }
    public Mono<RecoveryReservation> reserve(UUID operation,UUID challenge) {
        if(operation==null || challenge==null) return Mono.error(new RecoveryFailure(RecoveryFailure.Kind.INVALID));
        return call(RecoveryAction.RESERVE,operation,caller->api.reserveRecoveryVerification(operation,
            new ReserveRecoveryVerificationRequest().binding(caller.context().coreBinding()).challengeId(challenge))
            .switchIfEmpty(Mono.error(RecoveryFailure.unavailable())).map(reservation->{
                if(!operation.equals(reservation.getOperationId()) || !challenge.equals(reservation.getChallengeId()) || reservation.getReservationId()==null
                    || reservation.getProviderChallengeId()==null || reservation.getProviderChallengeId().isBlank() || reservation.getCategory()==null
                    || !instant(reservation.getExpiresAt()).isAfter(clock.instant())) throw RecoveryFailure.unavailable();
                return new RecoveryReservation(operation,challenge,reservation.getReservationId(),reservation.getProviderChallengeId(),
                    reservation.getCategory().getValue(),instant(reservation.getExpiresAt()));
            }));
    }
    public Mono<Void> complete(UUID operation,UUID challenge,UUID reservation,String attestation) {
        if(operation==null || challenge==null || reservation==null || attestation==null || attestation.isBlank() || attestation.length()>16384)
            return Mono.error(new RecoveryFailure(RecoveryFailure.Kind.INVALID));
        return call(RecoveryAction.COMPLETE,operation,caller->api.completeRecoveryVerification(operation,
            new CompleteRecoveryVerificationRequest().binding(caller.context().coreBinding()).challengeId(challenge).reservationId(reservation).attestation(attestation))
            .switchIfEmpty(Mono.error(RecoveryFailure.unavailable())).map(ack->{
                if(!operation.equals(ack.getOperationId()))throw RecoveryFailure.unavailable();return ack;
            })).then();
    }
    public Mono<RecoveryAuthorization> consume(UUID operation) {
        return call(RecoveryAction.CONSUME,operation,caller->api.consumeRecoveryAuthorization(operation,
            new ConsumeRecoveryAuthorizationRequest().binding(caller.context().coreBinding()).idempotencyKey(caller.context().ceremonyId()+":consume"))
            .switchIfEmpty(Mono.error(RecoveryFailure.unavailable())).map(receipt->authorization(receipt,operation,caller.context(),true)));
    }
    public Mono<RecoveryAuthorization> verify(UUID operation,UUID execution,Instant expiry) {
        return call(RecoveryAction.VERIFY,operation,caller->{
            checkReference(operation,execution,expiry,true);
            return api.verifyRecoveryAuthorization(operation,new VerifyRecoveryAuthorizationRequest().binding(caller.context().coreBinding())
                .executionId(execution).expiresAt(local(expiry))).switchIfEmpty(Mono.error(RecoveryFailure.unavailable())).map(receipt->{
                    var result=authorization(receipt,operation,caller.context(),true);
                    if(!execution.equals(result.executionId()) || !expiry.equals(result.expiresAt()))throw RecoveryFailure.refused();return result;
                });
        });
    }
    public Mono<Void> record(UUID operation,RecoveryAuthorization reference,String requestedOutcome) {
        return call(RecoveryAction.RECORD,operation,caller->{
            if(reference==null)throw new RecoveryFailure(RecoveryFailure.Kind.INVALID);
            checkReference(operation,reference.executionId(),reference.expiresAt(),false);
            if(!operation.equals(reference.operationId()) || reference.binding()!=null && !caller.context().coreBinding().equals(reference.binding()))throw RecoveryFailure.refused();
            return Mono.defer(()->nativePort.journal(caller.principal(),caller.context(),reference)).timeout(Duration.ofSeconds(5))
                .switchIfEmpty(Mono.error(RecoveryFailure.unavailable())).flatMap(journal->{
                    if(!reference.executionId().equals(journal.executionId()) || !Objects.equals(requestedOutcome,journal.outcome())
                        || !Set.of("PENDING","UNKNOWN","SUCCEEDED","FAILED").contains(journal.outcome()))throw RecoveryFailure.refused();
                    return api.recordRecoveryExecution(operation,new RecordRecoveryExecutionRequest().binding(caller.context().coreBinding())
                        .executionId(journal.executionId()).outcome(RecoveryExecutionOutcome.fromValue(journal.outcome())));
                });
        });
    }
    private RecoveryAuthorization authorization(RecoveryAuthorizationReceipt receipt,UUID operation,NativeRecoveryContext context,boolean future) {
        if(receipt==null || !Objects.equals(operation,receipt.getOperationId()) || !context.coreBinding().equals(receipt.getBinding()))throw RecoveryFailure.refused();
        Instant expiry=instant(receipt.getExpiresAt());checkReference(operation,receipt.getExecutionId(),expiry,future);
        return new RecoveryAuthorization(operation,receipt.getExecutionId(),receipt.getBinding(),expiry);
    }
    private void checkReference(UUID operation,UUID execution,Instant expiry,boolean future) {
        if(operation==null || execution==null || expiry==null || future && !expiry.isAfter(clock.instant()))throw RecoveryFailure.refused();
    }
    private <T> Mono<T> call(RecoveryAction action,UUID operation,Function<RecoveryAdmission.Caller,Mono<T>> invocation) {
        return Mono.defer(()->{
            if(action!=RecoveryAction.BEGIN && operation==null)throw new RecoveryFailure(RecoveryFailure.Kind.INVALID);
            return admission.admit(action,operation).flatMap(caller->Mono.defer(()->invocation.apply(caller))).timeout(Duration.ofSeconds(15));
        }).onErrorMap(error->error instanceof RecoveryFailure?error:RecoveryFailure.unavailable());
    }
    private static void sameContext(NativeRecoveryContext a,NativeRecoveryContext b) {
        if(!a.coreBinding().equals(b.coreBinding()))throw RecoveryFailure.refused();
    }
    // The original Java generator uses java8-localdatetime for RFC3339 Instant schemas; source fixture checks the actual generated type.
    private static Instant instant(LocalDateTime value) { if(value==null)throw RecoveryFailure.unavailable();return value.toInstant(ZoneOffset.UTC); }
    private static LocalDateTime local(Instant value) { return LocalDateTime.ofInstant(value,ZoneOffset.UTC); }
}
