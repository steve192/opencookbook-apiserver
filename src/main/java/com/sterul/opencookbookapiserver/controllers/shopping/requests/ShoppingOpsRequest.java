package com.sterul.opencookbookapiserver.controllers.shopping.requests;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ShoppingOpsRequest(@NotNull @Size(max = 200) List<@Valid ShoppingOpRequest> ops) {
}
