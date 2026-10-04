package com.sterul.opencookbookapiserver.controllers.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** @param input a link, or a recipe as text, as it was pasted, typed or shared */
public record RecipeImportRequest(@NotBlank @Size(max = RecipeImportRequest.MAX_LENGTH) String input) {

    public static final int MAX_LENGTH = 10_000;
}
