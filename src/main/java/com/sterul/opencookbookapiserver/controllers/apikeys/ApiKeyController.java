package com.sterul.opencookbookapiserver.controllers.apikeys;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.sterul.opencookbookapiserver.configurations.apikeys.ApiKeyAccess;
import com.sterul.opencookbookapiserver.configurations.apikeys.ConditionalOnApiKeysEnabled;
import com.sterul.opencookbookapiserver.configurations.security.NotForDemoAccounts;
import com.sterul.opencookbookapiserver.controllers.BaseController;
import com.sterul.opencookbookapiserver.controllers.apikeys.requests.ApiKeyRequest;
import com.sterul.opencookbookapiserver.controllers.apikeys.responses.ApiKeyResponse;
import com.sterul.opencookbookapiserver.controllers.apikeys.responses.CurrentApiKeyResponse;
import com.sterul.opencookbookapiserver.controllers.apikeys.responses.IssuedApiKeyResponse;
import com.sterul.opencookbookapiserver.entities.account.ApiKey;
import com.sterul.opencookbookapiserver.errors.ApiErrorCode;
import com.sterul.opencookbookapiserver.errors.ApiException;
import com.sterul.opencookbookapiserver.services.SignedInUserService;
import com.sterul.opencookbookapiserver.services.apikeys.ApiKeyService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** Managing keys needs a password login; a key only learns about itself. */
@RestController
@ConditionalOnApiKeysEnabled
@RequestMapping("/api/v1/api-keys")
@Tag(name = "Api keys", description = "Long-lived keys for headless clients such as Home Assistant")
public class ApiKeyController extends BaseController {

    private final ApiKeyService apiKeys;

    public ApiKeyController(ApiKeyService apiKeys, SignedInUserService signedInUser) {
        super(signedInUser);
        this.apiKeys = apiKeys;
    }

    @Operation(summary = "Your keys", description = "Newest first. The secrets themselves are never shown again.")
    @GetMapping
    public List<ApiKeyResponse> getKeys() {
        return apiKeys.keysOf(getLoggedInUser()).stream().map(ApiKeyResponse::of).toList();
    }

    @Operation(summary = "Create a key", description = "The answer carries the secret, and only this answer does. "
            + "A write scope brings its read scope.")
    @NotForDemoAccounts
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public IssuedApiKeyResponse create(@Valid @RequestBody ApiKeyRequest request) {
        return IssuedApiKeyResponse.of(apiKeys.create(getLoggedInUser(), request.name(), request.scopes()));
    }

    @Operation(summary = "Revoke a key", description = "Clients using it are signed out at their next request.")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@PathVariable Long id) {
        apiKeys.revoke(getLoggedInUser(), id);
    }

    @Operation(summary = "The key this request was made with",
            description = "For a client to check its key and learn what it may do.")
    @ApiKeyAccess
    @GetMapping("/current")
    public CurrentApiKeyResponse current(@AuthenticationPrincipal ApiKey key) {
        if (key == null) {
            throw new ApiException(ApiErrorCode.RESOURCE_NOT_FOUND, "Not signed in with an api key");
        }
        return CurrentApiKeyResponse.of(key);
    }
}
