package com.sterul.opencookbookapiserver.controllers.admin.responses;

import java.time.Instant;

import com.sterul.opencookbookapiserver.entities.Ingredient;

public record AdminIngredientResponse(
        Long id,
        String name,
        String additionalInfo,
        Long ownerUserId,
        String ownerEmailAddress,
        Long catalogueFoodId,
        String catalogueFoodKey,
        String catalogueFoodName,
        Ingredient.LinkSource linkSource,
        Float linkConfidence,
        Integer linkMatcherVersion,
        Instant linkedAt,
        boolean excludedFromNutrition,
        Instant createdOn,
        Instant lastChange) {

    public static AdminIngredientResponse fromEntity(Ingredient ingredient) {
        var owner = ingredient.getOwner();
        var food = ingredient.getCatalogueFood();
        return new AdminIngredientResponse(
                ingredient.getId(),
                ingredient.getName(),
                ingredient.getAdditionalInfo(),
                owner.getUserId(),
                owner.getEmailAddress(),
                food == null ? null : food.getId(),
                food == null ? null : food.getCatalogueKey(),
                food == null ? null : food.displayName("de").orElse(food.getSourceName()),
                ingredient.getLinkSource(),
                ingredient.getLinkConfidence(),
                ingredient.getLinkMatcherVersion(),
                ingredient.getLinkedAt(),
                ingredient.isExcludedFromNutrition(),
                ingredient.getCreatedOn(),
                ingredient.getLastChange());
    }
}
