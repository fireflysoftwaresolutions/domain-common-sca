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

import io.fireflysoftware.domain.common.sca.core.recovery.RecoveryTrust;
import org.fireflyframework.security.rs.ResourceServerProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class RecoveryConfigurationTest {
    @TempDir Path directory;
    @Test void httpJwksOptionalTenantSymmetricAlgorithmsAndPublicPermitCannotEnableReceiver() {
        var trust=new RecoveryTrust("https://identity.example/realms/customers","customers","HUMAN",
            Set.of("firefly-recovery-domain","firefly-recovery-core"),Set.of("configured-client"),"PERMANENT_AUTHENTICATION_UNLOCK",300);
        for(String invalid:List.of("http","optionalTenant","algorithm","permit")) {
            var p=new ResourceServerProperties();p.setIssuer(trust.issuer());p.setAudiences(new ArrayList<>(trust.audiences()));
            p.setJwkSetUri("https://identity.example/realms/customers/protocol/openid-connect/certs");
            p.setTenantRequired(true);p.setTenantClaimPaths(List.of("tenant_id"));p.setStrictPolicy(true);
            switch(invalid){case "http"->p.setJwkSetUri("http://remote.example/keys");case "optionalTenant"->p.setTenantRequired(false);
                case "algorithm"->p.setSignatureAlgorithms(List.of("HS256"));case "permit"->p.setPermitMatchers(List.of("/**"));}
            assertThatThrownBy(()->RecoverySecurityConfiguration.validate(p,new MockEnvironment().withProperty("firefly.security.resource-server.enabled","true"),trust))
                .isInstanceOf(IllegalStateException.class);
        }
        assertThatThrownBy(()->new RecoveryTrust("http://remote.example/realms/customers","customers","HUMAN",trust.audiences(),trust.clients(),trust.purpose(),300))
            .isInstanceOf(IllegalArgumentException.class);
    }
    @Test void workloadSecretMustBeExplicitOwnerOnlyRegularFileAndCannotBeSymlink() throws Exception {
        var secret=directory.resolve("workload-secret");String value="synthetic-owner-only-workload-secret";
        Files.writeString(secret,value);Files.setPosixFilePermissions(secret,PosixFilePermissions.fromString("rw-------"));
        assertThat(NativeRecoveryHttpConfiguration.readSecret(secret.toString())).isEqualTo(value);
        Files.setPosixFilePermissions(secret,PosixFilePermissions.fromString("rw-r--r--"));
        assertThatThrownBy(()->NativeRecoveryHttpConfiguration.readSecret(secret.toString())).isInstanceOf(IllegalStateException.class);
        Files.setPosixFilePermissions(secret,PosixFilePermissions.fromString("rw-------"));
        var link=directory.resolve("linked-secret");Files.createSymbolicLink(link,secret);
        assertThatThrownBy(()->NativeRecoveryHttpConfiguration.readSecret(link.toString())).isInstanceOf(IllegalStateException.class);
    }
}
