package com.sterul.opencookbookapiserver.controllers.baking;

import com.sterul.opencookbookapiserver.entities.baking.SoakingFood;

/**
 * @param foodId     the catalogue food
 * @param absorption grams of water a gram soaks up
 */
public record SoakingFoodResponse(Long foodId, float absorption) {

    public static SoakingFoodResponse of(SoakingFood food) {
        return new SoakingFoodResponse(food.getId(), food.getAbsorption());
    }
}
