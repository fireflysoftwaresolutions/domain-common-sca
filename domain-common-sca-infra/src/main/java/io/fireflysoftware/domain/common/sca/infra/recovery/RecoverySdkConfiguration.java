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


package io.fireflysoftware.domain.common.sca.infra.recovery;

import io.fireflysoftware.common.sca.sdk.api.ScaRecoveryApi;
import io.fireflysoftware.common.sca.sdk.invoker.ApiClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import java.net.URI;
import java.time.Duration;

@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name="firefly.sca.recovery.enabled",havingValue="true")
public class RecoverySdkConfiguration {
    @Bean public ScaRecoveryApi recoveryCoreApi(@Value("${firefly.sca.recovery.core-origin}") String coreOrigin) {
        URI origin=URI.create(coreOrigin);
        WebClient web=WebClient.builder().clientConnector(new ReactorClientHttpConnector(HttpClient.create()
            .followRedirect(false).responseTimeout(Duration.ofSeconds(10))))
            .exchangeStrategies(UtcRecoveryCodecs.strategies())
            .filter(new ScopedRecoveryBearer(origin)).build();
        ApiClient client=new ApiClient(web);
        client.setBasePath(coreOrigin.endsWith("/")?coreOrigin.substring(0,coreOrigin.length()-1):coreOrigin);
        return new ScaRecoveryApi(client);
    }
}
