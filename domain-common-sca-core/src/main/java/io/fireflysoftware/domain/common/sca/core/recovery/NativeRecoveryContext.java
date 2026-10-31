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

import io.fireflysoftware.common.sca.sdk.model.RecoveryBinding;
import java.time.Instant;

/** Returned only after independent native current owner, generation and restricted-ceremony verification. */
public record NativeRecoveryContext(String issuer,String tenant,String clientId,String subject,String realm,String population,
        String actorId,String ceremonyId,String lockGeneration,String purpose,Instant validUntil) {
    public RecoveryBinding coreBinding() {
        return new RecoveryBinding().issuer(issuer).tenant(tenant).clientId(clientId).actorSubject(subject)
            .targetIssuer(issuer).targetRealm(realm).targetSubject(subject).population(population).purpose(purpose).lockGeneration(lockGeneration);
    }
    @Override public String toString() { return "NativeRecoveryContext[redacted]"; }
}
