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

import org.fireflyframework.security.api.domain.SecurityPrincipal;
import org.fireflyframework.security.webflux.authentication.FireflyAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;

/** Keeps decoder-validated credentials only in this request's authenticated reactive context for scoped relay. */
public final class RecoveryAuthenticationToken extends FireflyAuthenticationToken {
    private final transient Jwt jwt;
    public RecoveryAuthenticationToken(SecurityPrincipal principal,Jwt jwt){super(principal);this.jwt=jwt;}
    @Override public Object getCredentials(){return jwt;}
    @Override public String toString(){return "RecoveryAuthenticationToken[redacted]";}
}
