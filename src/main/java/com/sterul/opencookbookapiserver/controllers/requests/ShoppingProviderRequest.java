package com.sterul.opencookbookapiserver.controllers.requests;

import com.sterul.opencookbookapiserver.entities.shopping.ShoppingProvider;

import jakarta.validation.constraints.NotNull;

public record ShoppingProviderRequest(@NotNull ShoppingProvider provider) {
}
