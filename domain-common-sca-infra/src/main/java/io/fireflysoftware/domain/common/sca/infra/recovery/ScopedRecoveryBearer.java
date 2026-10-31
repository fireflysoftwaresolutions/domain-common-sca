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

import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.core.AbstractOAuth2Token;
import org.springframework.web.reactive.function.client.*;
import reactor.core.publisher.Mono;
import java.net.URI;

/** Per-subscription bearer relay to one configured Core recovery origin, with no mutable token state. */
public final class ScopedRecoveryBearer implements ExchangeFilterFunction {
    private final URI origin;
    public ScopedRecoveryBearer(URI origin) {
        if(origin==null || !"https".equals(origin.getScheme()) || origin.getHost()==null || origin.getUserInfo()!=null
            || origin.getRawQuery()!=null || origin.getFragment()!=null || !SetPath.root(origin.getRawPath()))
            throw new IllegalArgumentException("Explicit HTTPS Core recovery origin required");
        this.origin=origin;
    }
    @Override public Mono<ClientResponse> filter(ClientRequest request,ExchangeFunction next) {
        return Mono.defer(()->{
            URI uri=request.url();
            if(request.method()!=org.springframework.http.HttpMethod.POST || !origin.getScheme().equals(uri.getScheme()) || !origin.getHost().equals(uri.getHost()) || port(origin)!=port(uri)
                || uri.getUserInfo()!=null || uri.getRawQuery()!=null || uri.getFragment()!=null
                || !uri.getRawPath().matches("/api/v1/sca/recovery-operations(?:/[0-9a-fA-F-]{36}/(?:challenges|verifications/(?:reservations|complete)|authorizations(?:/verify)?|executions))?")
                || request.headers().containsKey(HttpHeaders.AUTHORIZATION))return Mono.error(refused());
            return ReactiveSecurityContextHolder.getContext().flatMap(context->Mono.justOrEmpty(context.getAuthentication()))
                .filter(authentication->authentication!=null && authentication.isAuthenticated())
                .map(authentication->authentication.getCredentials()).filter(AbstractOAuth2Token.class::isInstance).cast(AbstractOAuth2Token.class)
                .filter(token->token.getExpiresAt()!=null && token.getExpiresAt().isAfter(java.time.Instant.now()))
                .switchIfEmpty(Mono.error(refused())).flatMap(token->next.exchange(ClientRequest.from(request)
                    .headers(headers->headers.setBearerAuth(token.getTokenValue())).build()));
        });
    }
    private static int port(URI uri){return uri.getPort()<0?443:uri.getPort();}
    private static IllegalStateException refused(){return new IllegalStateException("Validated scoped recovery bearer required");}
    private static final class SetPath { private static boolean root(String path){return path==null || path.isEmpty() || path.equals("/");} }
}
