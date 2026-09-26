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

    // Not required: this shape is sent both to name a group and as the group of a recipe, and
    // in the second case the caller sends the id alone. Validating it here would reject that.
    private String title;

}
