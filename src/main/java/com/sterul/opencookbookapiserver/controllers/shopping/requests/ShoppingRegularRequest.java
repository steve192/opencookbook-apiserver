package com.sterul.opencookbookapiserver.controllers.shopping.requests;

import jakarta.validation.constraints.NotNull;

/** @param muted not to be suggested */
public record ShoppingRegularRequest(@NotNull Boolean muted) {
}
