package com.sterul.opencookbookapiserver.controllers.nutrition;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;

/** {@code {"catalogueFoodId": 12}} or {@code {"excluded": true}} for something without nutrients. */
public record IngredientLinkRequest(Long catalogueFoodId, Boolean excluded) {

    public boolean isExcluded() {
        return Boolean.TRUE.equals(excluded);
    }

    @AssertTrue(message = "either a catalogue food or excluded")
    @Schema(hidden = true)
    public boolean isEitherAFoodOrExcluded() {
        return isExcluded() == (catalogueFoodId == null);
    }
}
