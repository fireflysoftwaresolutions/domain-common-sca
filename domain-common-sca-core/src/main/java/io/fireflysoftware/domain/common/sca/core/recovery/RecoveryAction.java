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

public enum RecoveryAction {
    BEGIN("sca.recovery.create"), ISSUE("sca.recovery.challenge.issue"),
    RESERVE("sca.recovery.verification.reserve"), COMPLETE("sca.recovery.verification.complete"),
    CONSUME("sca.recovery.authorization.consume"), VERIFY("sca.recovery.authorization.verify"),
    RECORD("sca.recovery.execution.record");
    private final String policyAction;
    RecoveryAction(String policyAction) { this.policyAction=policyAction; }
    public String policyAction() { return policyAction; }
    public String nativeAction() { return "DOMAIN_"+switch(this) {
        case BEGIN->"CREATE";case ISSUE->"ISSUE_CHALLENGE";case RESERVE->"RESERVE_VERIFICATION";
        case COMPLETE->"COMPLETE_VERIFICATION";case CONSUME->"CONSUME";case VERIFY->"VERIFY_AUTHORIZATION";case RECORD->"RECORD_EXECUTION";}; }

}
