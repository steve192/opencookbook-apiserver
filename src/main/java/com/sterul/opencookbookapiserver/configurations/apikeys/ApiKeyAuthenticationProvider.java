package com.sterul.opencookbookapiserver.configurations.apikeys;

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;

import com.sterul.opencookbookapiserver.services.apikeys.ApiKeyService;

public class ApiKeyAuthenticationProvider implements AuthenticationProvider {

    private final ApiKeyService apiKeys;

    public ApiKeyAuthenticationProvider(ApiKeyService apiKeys) {
        this.apiKeys = apiKeys;
    }

    @Override
    public Authentication authenticate(Authentication authentication) {
        var secret = ((ApiKeyAuthenticationToken) authentication).getCredentials();
        return apiKeys.authenticate(secret)
                .map(ApiKeyAuthenticationToken::authenticated)
                .orElseThrow(() -> new BadCredentialsException("Unknown or revoked api key"));
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return ApiKeyAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
