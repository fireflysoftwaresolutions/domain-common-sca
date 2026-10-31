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

import java.net.URI;
import java.util.Set;

public record RecoveryTrust(String issuer,String realm,String population,Set<String> audiences,Set<String> clients,String purpose,int maxLifetimeSeconds) {
    public RecoveryTrust {
        URI uri=URI.create(issuer);
        if (!"https".equals(uri.getScheme()) || uri.getHost()==null || uri.getUserInfo()!=null || uri.getQuery()!=null || uri.getFragment()!=null
                || realm==null || realm.isBlank() || Set.of("master","sandbox-institutions").contains(realm)
                || !uri.getRawPath().equals("/realms/"+realm) || !Set.of("HUMAN","STAFF").contains(population)
                || audiences==null || !audiences.equals(Set.of("firefly-recovery-domain","firefly-recovery-core")) || invalid(audiences) || clients==null || clients.isEmpty() || invalid(clients)
                || !"PERMANENT_AUTHENTICATION_UNLOCK".equals(purpose) || maxLifetimeSeconds<1 || maxLifetimeSeconds>300 || population.equals("STAFF") && maxLifetimeSeconds>180) {
            throw new IllegalArgumentException("Explicit isolated population and HTTPS restricted-recovery trust required");
        }
        audiences=Set.copyOf(audiences); clients=Set.copyOf(clients);
    }
    private static boolean invalid(Set<String> values) { return values.stream().anyMatch(v->v==null || v.isBlank() || !v.equals(v.strip())); }
}
