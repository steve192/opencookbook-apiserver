package com.sterul.opencookbookapiserver.controllers.apikeys.requests;

import java.util.Set;

import com.sterul.opencookbookapiserver.entities.account.ApiScope;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ApiKeyRequest(@NotBlank @Size(max = 60) String name, @NotEmpty Set<@NotNull ApiScope> scopes) {
}
