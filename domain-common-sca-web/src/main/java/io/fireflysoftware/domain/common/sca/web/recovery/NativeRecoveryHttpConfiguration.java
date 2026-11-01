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
import io.fireflysoftware.domain.common.sca.core.recovery.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import io.netty.channel.ChannelOption;
import java.net.URI;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Duration;
import java.util.Set;

@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name="firefly.sca.recovery.enabled",havingValue="true")
public class NativeRecoveryHttpConfiguration {
    @Configuration(proxyBeanMethods=false)
    @ConditionalOnProperty(name={"firefly.sca.recovery.enabled","firefly.sca.recovery.native-http.enabled"},havingValue="true")
    static class Enabled {
        @Bean NativeRecoveryHttpClientImpl nativeRecoveryHttpClient(RecoveryTrust trust,
            @Value("${firefly.sca.recovery.native-http.base-uri}") String baseUri,
            @Value("${firefly.sca.recovery.native-http.workload-username}") String username,
            @Value("${firefly.sca.recovery.native-http.workload-password-file}") String passwordFile,
            @Value("${firefly.sca.recovery.native-http.timeout-seconds}") int seconds,
            @Value("${firefly.sca.recovery.native-http.provider-profile}") String profile){
            if(!"firefly-keycloak-native-v1".equals(profile) || !baseUri.equals(trust.issuer()+"/firefly-recovery/rpc")
                || username==null || username.isBlank() || username.contains(":") || seconds<1 || seconds>10)
                throw new IllegalStateException("Explicit native provider HTTPS/workload profile required");
            String password=readSecret(passwordFile);
            WebClient web=WebClient.builder().clientConnector(new ReactorClientHttpConnector(HttpClient.create().followRedirect(false)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS,seconds*1000).responseTimeout(Duration.ofSeconds(seconds))))
                .codecs(codecs->codecs.defaultCodecs().maxInMemorySize(65536)).build();
            return new NativeRecoveryHttpClientImpl(web,URI.create(baseUri),username,password,Duration.ofSeconds(seconds));
        }
        @Bean NativeRecoveryPort nativeRecoveryHttpPort(NativeRecoveryHttpClientImpl http,RecoveryTrust trust){return new NativeRecoveryHttpPortImpl(http,trust);}
        @Bean RecoveryAuditPort nativeRecoveryAuditPort(NativeRecoveryHttpClientImpl http){return new RecoveryAuditHttpPortImpl(http);}
    }
    static String readSecret(String file){
        try{Path path=Path.of(file);if(!path.isAbsolute() || Files.isSymbolicLink(path) || !Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)
            || Files.size(path)>4096)throw new IllegalArgumentException();
            Set<PosixFilePermission> mode=Files.getPosixFilePermissions(path,LinkOption.NOFOLLOW_LINKS);
            if(mode.stream().anyMatch(permission->permission.name().startsWith("GROUP_") || permission.name().startsWith("OTHERS_")))throw new IllegalArgumentException();
            String value=Files.readString(path);if(value.endsWith("\n"))value=value.substring(0,value.length()-1);if(value.endsWith("\r"))value=value.substring(0,value.length()-1);
            if(value.length()<32 || value.isBlank() || value.contains("\n") || value.contains("\r"))throw new IllegalArgumentException();return value;
        }catch(Exception error){throw new IllegalStateException("Protected native workload secret file required");}
    }
}
