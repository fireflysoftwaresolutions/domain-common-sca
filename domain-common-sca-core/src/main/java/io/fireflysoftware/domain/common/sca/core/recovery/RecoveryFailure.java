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

public final class RecoveryFailure extends RuntimeException {
    public enum Kind { UNAUTHENTICATED, REFUSED, UNAVAILABLE, INVALID }
    private final Kind kind;
    public RecoveryFailure(Kind kind) { super("Recovery request unavailable or refused", null, false, false); this.kind=kind; }
    public Kind kind() { return kind; }
    public static RecoveryFailure refused() { return new RecoveryFailure(Kind.REFUSED); }
    public static RecoveryFailure unavailable() { return new RecoveryFailure(Kind.UNAVAILABLE); }
}
