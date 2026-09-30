package com.sterul.opencookbookapiserver.controllers.apikeys.responses;

import com.sterul.opencookbookapiserver.services.apikeys.ApiKeyService.IssuedKey;

public record IssuedApiKeyResponse(ApiKeyResponse key, String secret) {

    public static IssuedApiKeyResponse of(IssuedKey issued) {
        return new IssuedApiKeyResponse(ApiKeyResponse.of(issued.key()), issued.secret());
    }
}
