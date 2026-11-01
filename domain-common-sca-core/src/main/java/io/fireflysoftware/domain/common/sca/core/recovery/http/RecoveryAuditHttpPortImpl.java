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


package io.fireflysoftware.domain.common.sca.core.recovery.http;

import io.fireflysoftware.domain.common.sca.core.recovery.*;
import reactor.core.publisher.Mono;
import java.time.Instant;

/** Requires native committed audit receipt; no log/no-op acknowledgement can satisfy admission. */
public final class RecoveryAuditHttpPortImpl implements RecoveryAuditPort {
    private final NativeRecoveryHttpClientImpl http;
    public RecoveryAuditHttpPortImpl(NativeRecoveryHttpClientImpl http){this.http=http;}
    @Override public boolean isQualified(){return true;}
    @Override public Mono<Void> record(Event event){
        String action=event.outcome()==Outcome.ADMITTED?"DOMAIN_BOUNDARY_ADMITTED":"DOMAIN_BOUNDARY_REFUSED";
        return http.assertion(false).defaultIfEmpty("").flatMap(assertion->http.post("audit",new NativeRecoveryHttpClientImpl.Rpc(assertion.isEmpty()?null:assertion,
            null,null,null,null,null,null,null,null,null,action),NativeRecoveryHttpClientImpl.AuditReceipt.class)).flatMap(receipt->{
                Instant now=Instant.now();if(receipt.auditId()==null || receipt.committedAt()==null || receipt.committedAt().isAfter(now.plusSeconds(30))
                    || receipt.committedAt().isBefore(now.minusSeconds(60)))return Mono.error(RecoveryFailure.unavailable());return Mono.<Void>empty();
            });
    }
}
