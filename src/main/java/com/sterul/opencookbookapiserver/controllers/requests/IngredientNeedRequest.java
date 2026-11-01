package com.sterul.opencookbookapiserver.controllers.requests;

import com.fasterxml.jackson.annotation.JsonIgnore;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Released apps send the editor's blank line along, so an empty line is skipped, not rejected. */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class IngredientNeedRequest {

    private Float amount;
    private String unit;

    @Size(max = 500)
    private String note;

    @Valid
    private IngredientReference ingredient;

    @JsonIgnore
    @Schema(hidden = true)
    public boolean isEmpty() {
        return !hasIngredientName() && amount == null && isBlank(unit) && isBlank(note);
    }

    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "A line with an amount, a unit or a note needs an ingredient name")
    public boolean isIngredientNamedOrLineEmpty() {
        return hasIngredientName() || isEmpty();
    }

    private boolean hasIngredientName() {
        return ingredient != null && !isBlank(ingredient.getName());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
