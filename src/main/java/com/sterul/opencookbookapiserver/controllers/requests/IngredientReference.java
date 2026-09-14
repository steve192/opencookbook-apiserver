package com.sterul.opencookbookapiserver.controllers.requests;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Identified by name among the caller's ingredients; ids are ignored, as they could be somebody else's. */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class IngredientReference {

    private String name;
}
