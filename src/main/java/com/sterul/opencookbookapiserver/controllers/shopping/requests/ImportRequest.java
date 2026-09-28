package com.sterul.opencookbookapiserver.controllers.shopping.requests;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** @param shown every line the sheet offered, ticked or not; how staples are learned */
public record ImportRequest(
        @NotEmpty @Size(max = 300) List<@Valid ImportLineRequest> lines,
        @NotNull @Size(max = 300) List<@Valid ShownLineRequest> shown) {
}
