package com.sterul.opencookbookapiserver.controllers.shopping.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ShoppingListRequest(@NotBlank @Size(max = 64) String name) {
}
