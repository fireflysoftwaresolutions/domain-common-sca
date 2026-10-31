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
import io.fireflysoftware.common.sca.sdk.api.ScaRecoveryApi;
import org.fireflyframework.security.spi.*;
import org.fireflyframework.security.rs.ResourceServerProperties;
import org.fireflyframework.security.webflux.authentication.FireflyAuthenticationToken;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.env.Environment;
import org.springframework.http.*;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.web.access.server.BearerTokenServerAccessDeniedHandler;
import org.springframework.security.oauth2.server.resource.web.server.BearerTokenServerAuthenticationEntryPoint;
import org.springframework.security.web.server.*;
import org.springframework.security.web.server.authentication.HttpStatusServerEntryPoint;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import reactor.core.publisher.Mono;
import java.net.URI;
import java.time.*;
import java.util.*;

@Configuration(proxyBeanMethods=false)
@EnableWebFluxSecurity
@EnableConfigurationProperties(ResourceServerProperties.class)
public class RecoverySecurityConfiguration {
    @Bean @ConditionalOnMissingBean @ConditionalOnProperty(name="firefly.sca.recovery.native-http.enabled",havingValue="false",matchIfMissing=true) public NativeRecoveryPort unavailableNativeRecovery() {return new NativeRecoveryPort(){
        public Mono<NativeRecoveryContext> admit(org.fireflyframework.security.api.domain.SecurityPrincipal principal,String purpose,RecoveryAction action,java.util.UUID operationId){return Mono.error(RecoveryFailure.unavailable());}
        public Mono<NativeExecutionJournal> journal(org.fireflyframework.security.api.domain.SecurityPrincipal principal,NativeRecoveryContext context,RecoveryAuthorization reference){return Mono.error(RecoveryFailure.unavailable());}
    };}
    @Bean @ConditionalOnMissingBean @ConditionalOnProperty(name="firefly.sca.recovery.native-http.enabled",havingValue="false",matchIfMissing=true) public RecoveryAuditPort unavailableRecoveryAudit(){return event->Mono.error(RecoveryFailure.unavailable());}
    @Bean @ConditionalOnProperty(name="firefly.sca.recovery.enabled",havingValue="false",matchIfMissing=true)
    public SecurityWebFilterChain disabled(ServerHttpSecurity http){
        return stateless(http).exceptionHandling(errors->errors.authenticationEntryPoint(new HttpStatusServerEntryPoint(HttpStatus.FORBIDDEN))
            .accessDeniedHandler((exchange,error)->{exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);return exchange.getResponse().setComplete();}))
            .authorizeExchange(exchanges->exchanges.anyExchange().denyAll()).build();
    }
    @Bean @ConditionalOnProperty(name="firefly.sca.recovery.enabled",havingValue="true")
    public RecoveryTrust recoveryTrust(@Value("${firefly.sca.recovery.issuer}") String issuer,@Value("${firefly.sca.recovery.realm}") String realm,
        @Value("${firefly.sca.recovery.population}") String population,@Value("${firefly.sca.recovery.audiences}") Set<String> audiences,
        @Value("${firefly.sca.recovery.allowed-clients}") Set<String> clients,@Value("${firefly.sca.recovery.max-lifetime-seconds}") int ttl){
        return new RecoveryTrust(issuer,realm,population,audiences,clients,"PERMANENT_AUTHENTICATION_UNLOCK",ttl);
    }
    @Bean @ConditionalOnProperty(name="firefly.sca.recovery.enabled",havingValue="true")
    public RecoveryAdmission recoveryAdmission(SecurityContextPort security,PolicyDecisionPort policy,NativeRecoveryPort nativePort,RecoveryAuditPort audit,RecoveryTrust trust){
        return new RecoveryAdmission(security,policy,nativePort,audit,trust,Clock.systemUTC());
    }
    @Bean @ConditionalOnProperty(name="firefly.sca.recovery.enabled",havingValue="true")
    public RecoveryOrchestrator recoveryOrchestrator(ScaRecoveryApi api,RecoveryAdmission admission,NativeRecoveryPort nativePort){
        return new RecoveryOrchestrator(api,admission,nativePort,Clock.systemUTC());
    }
    @Bean @ConditionalOnProperty(name="firefly.sca.recovery.enabled",havingValue="true")
    public SecurityWebFilterChain enabled(ServerHttpSecurity http,ReactiveJwtDecoder decoder,Converter<Jwt,Mono<AbstractAuthenticationToken>> converter,
        ResourceServerProperties properties,Environment environment,RecoveryTrust trust,RecoveryAuditPort audit){
        validate(properties,environment,trust);
        if(!audit.isQualified())throw new IllegalStateException("Qualified durable recovery audit required");
        ServerAuthenticationEntryPoint entry=(exchange,error)->refusal(audit).then(Mono.defer(()->new BearerTokenServerAuthenticationEntryPoint()
            .commence(exchange,new BadCredentialsException("Verified recovery assertion required")))).onErrorResume(failure->unavailable(exchange));
        ServerAccessDeniedHandler denied=(exchange,error)->refusal(audit).then(Mono.defer(()->new BearerTokenServerAccessDeniedHandler()
            .handle(exchange,new AccessDeniedException("Recovery route refused")))).onErrorResume(failure->unavailable(exchange));
        String base="/api/v1/sca/recovery-operations";
        Converter<Jwt,Mono<AbstractAuthenticationToken>> relay=jwt->converter.convert(jwt).flatMap(authentication->{
            if(!(authentication instanceof FireflyAuthenticationToken firefly))return Mono.error(new BadCredentialsException("Verified principal projection required"));
            return Mono.just(new RecoveryAuthenticationToken(firefly.securityPrincipal(),jwt));
        });
        return stateless(http).exceptionHandling(errors->errors.authenticationEntryPoint(entry).accessDeniedHandler(denied))
            .authorizeExchange(exchanges->exchanges.pathMatchers(HttpMethod.POST,base,base+"/*/verifications/reservations",base+"/*/verifications/complete",
                base+"/*/authorizations",base+"/*/authorizations/verify",base+"/*/executions").authenticated().anyExchange().denyAll())
            .oauth2ResourceServer(oauth->oauth.authenticationEntryPoint(entry).accessDeniedHandler(denied)
                .jwt(jwt->jwt.jwtDecoder(decoder).jwtAuthenticationConverter(relay))).build();
    }
    public static void validate(ResourceServerProperties p,Environment e,RecoveryTrust trust){
        Set<String> asymmetric=Set.of("RS256","RS384","RS512","PS256","PS384","PS512","ES256","ES384","ES512");
        if(!e.getProperty("firefly.security.resource-server.enabled",Boolean.class,false) || !trust.issuer().equals(p.getIssuer())
            || p.getAudiences().size()!=trust.audiences().size() || !new HashSet<>(p.getAudiences()).equals(trust.audiences())
            || !p.isTenantRequired() || !p.getTenantClaimPaths().equals(List.of("tenant_id")) || !p.isStrictPolicy() || !p.getPermitMatchers().isEmpty()
            || p.getSignatureAlgorithms().isEmpty() || !asymmetric.containsAll(p.getSignatureAlgorithms()))throw new IllegalStateException("Explicit strict recovery resource server required");
        try{URI jwks=URI.create(p.getJwkSetUri());if(!"https".equals(jwks.getScheme()) || jwks.getHost()==null || jwks.getUserInfo()!=null
            || jwks.getRawQuery()!=null || jwks.getFragment()!=null)throw new IllegalArgumentException();}
        catch(RuntimeException failure){throw new IllegalStateException("Explicit HTTPS JWKS required");}
    }
    private static ServerHttpSecurity stateless(ServerHttpSecurity http){return http.securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
        .csrf(ServerHttpSecurity.CsrfSpec::disable).httpBasic(ServerHttpSecurity.HttpBasicSpec::disable).formLogin(ServerHttpSecurity.FormLoginSpec::disable).logout(ServerHttpSecurity.LogoutSpec::disable);}
    private static Mono<Void> refusal(RecoveryAuditPort audit){return Mono.defer(()->audit.record(new RecoveryAuditPort.Event(null,null,RecoveryAuditPort.Outcome.REFUSED))).timeout(Duration.ofSeconds(5));}
    private static Mono<Void> unavailable(org.springframework.web.server.ServerWebExchange exchange){exchange.getResponse().setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);return exchange.getResponse().setComplete();}
}
