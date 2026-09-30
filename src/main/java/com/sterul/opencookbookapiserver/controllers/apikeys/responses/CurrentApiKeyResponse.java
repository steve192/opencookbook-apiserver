package com.sterul.opencookbookapiserver.controllers.apikeys.responses;

import java.util.List;

import com.sterul.opencookbookapiserver.controllers.support.DisplayNames;
import com.sterul.opencookbookapiserver.entities.account.ApiKey;
import com.sterul.opencookbookapiserver.entities.account.ApiScope;

/**
 * @param accountId the owner's, stable across keys, so a client can tell a new key of the same account apart
 * @param owner the key owner's display name, as household members see it
 */
public record CurrentApiKeyResponse(Long id, String name, List<ApiScope> scopes, Long accountId, String owner) {

    public static CurrentApiKeyResponse of(ApiKey key) {
        return new CurrentApiKeyResponse(key.getId(), key.getName(), key.getScopes().stream().sorted().toList(),
                key.getOwner().getUserId(), DisplayNames.of(key.getOwner()));
    }
}
