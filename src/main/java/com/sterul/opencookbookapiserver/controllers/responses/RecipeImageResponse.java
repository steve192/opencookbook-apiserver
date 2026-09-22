package com.sterul.opencookbookapiserver.controllers.responses;

import com.sterul.opencookbookapiserver.entities.RecipeImage;

public record RecipeImageResponse(String uuid) {

    public static RecipeImageResponse fromEntity(RecipeImage image) {
        return new RecipeImageResponse(image.getUuid());
    }
}
