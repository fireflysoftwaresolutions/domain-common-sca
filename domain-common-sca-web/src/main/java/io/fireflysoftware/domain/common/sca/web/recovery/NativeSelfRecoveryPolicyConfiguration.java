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
import org.fireflyframework.security.spi.PolicyDecisionPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import java.time.Clock;

@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name={"firefly.sca.recovery.enabled","firefly.sca.recovery.native-http.enabled","firefly.sca.recovery.native-self-service-policy.enabled"},havingValue="true")
public class NativeSelfRecoveryPolicyConfiguration {
    @Bean PolicyDecisionPort nativeSelfRecoveryPolicy(NativeRecoveryPort nativePort,RecoveryTrust trust){return new NativeSelfRecoveryPolicy(nativePort,trust,Clock.systemUTC());}
}
