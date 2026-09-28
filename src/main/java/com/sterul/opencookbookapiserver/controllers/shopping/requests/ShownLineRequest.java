package com.sterul.opencookbookapiserver.controllers.shopping.requests;

import com.sterul.opencookbookapiserver.services.shopping.StapleService;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A line an import offered, and whether it was taken; how staples are learned. */
public record ShownLineRequest(@NotBlank @Size(max = 120) String name, boolean ticked) {

    public StapleService.ObservedLine toObservation() {
        return new StapleService.ObservedLine(name, ticked);
    }
}
