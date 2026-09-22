package com.sterul.opencookbookapiserver.controllers.households.requests;

import jakarta.validation.constraints.NotNull;

/** Always about the caller's own membership; the API deliberately takes no user id. */
public record SharingRequest(@NotNull Boolean shareRecipes) {
}
