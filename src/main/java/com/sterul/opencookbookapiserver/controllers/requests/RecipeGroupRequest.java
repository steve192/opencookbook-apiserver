package com.sterul.opencookbookapiserver.controllers.requests;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RecipeGroupRequest {

    private Long id;

    // Temporarily disabled. When a recipe has a recipe group, it does not have a title
    // TODO: Different class for recipe group creation / update and recipe creation / update
    // @NotNull
    // @NotEmpty
    private String title;

}
