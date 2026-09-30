package com.sterul.opencookbookapiserver.controllers.apikeys.responses;

import java.time.Instant;
import java.util.List;

import com.sterul.opencookbookapiserver.entities.account.ApiKey;
import com.sterul.opencookbookapiserver.entities.account.ApiScope;

/** @param lastUsedAt null until first used; precise to about a minute */
public record ApiKeyResponse(Long id, String name, String displayPrefix, List<ApiScope> scopes, Instant createdOn,
        Instant lastUsedAt) {

    public static ApiKeyResponse of(ApiKey key) {
        return new ApiKeyResponse(key.getId(), key.getName(), key.getDisplayPrefix(),
                key.getScopes().stream().sorted().toList(), key.getCreatedOn(), key.getLastUsedAt());
    }
}
