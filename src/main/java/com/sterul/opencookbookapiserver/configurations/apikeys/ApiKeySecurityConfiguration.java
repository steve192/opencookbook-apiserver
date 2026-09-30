package com.sterul.opencookbookapiserver.configurations.apikeys;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.AuthenticationEntryPointFailureHandler;
import org.springframework.security.web.authentication.AuthenticationFilter;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import com.sterul.opencookbookapiserver.configurations.security.BearerTokens;
import com.sterul.opencookbookapiserver.services.apikeys.ApiKeyService;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Requests that carry an api key get a filter chain of their own, ahead of the one for password
 * logins. A key reaches the endpoints marked with ApiKeyAccess, with the scope named there; everything
 * else is refused, so a new endpoint stays closed to keys until it is marked.
 */
@Configuration
@ConditionalOnApiKeysEnabled
public class ApiKeySecurityConfiguration {

    @Bean
    @Order(1)
    SecurityFilterChain apiKeyFilterChain(HttpSecurity http, ApiKeyService apiKeys,
            @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlerMapping,
            AuthenticationEntryPoint authenticationRequired, AccessDeniedHandler accessDenied) {
        var authentication = new AuthenticationFilter(new ProviderManager(new ApiKeyAuthenticationProvider(apiKeys)),
                request -> apiKeyOf(request).map(ApiKeyAuthenticationToken::unauthenticated).orElse(null));
        // On to the endpoint, rather than a redirect as after a login form.
        authentication.setSuccessHandler((request, response, result) -> { });
        authentication.setFailureHandler(new AuthenticationEntryPointFailureHandler(authenticationRequired));

        http.securityMatcher(request -> apiKeyOf(request).isPresent())
                .authorizeHttpRequests(authorize -> {
                    ApiKeyEndpoints.open(authorize, handlerMapping.getHandlerMethods());
                    authorize.anyRequest().denyAll();
                })
                .addFilterBefore(authentication, BasicAuthenticationFilter.class)
                .exceptionHandling(exceptions -> exceptions.accessDeniedHandler(accessDenied));
        return http.build();
    }

    private static Optional<String> apiKeyOf(HttpServletRequest request) {
        return BearerTokens.of(request).filter(ApiKeyService::looksLikeKey);
    }
}
