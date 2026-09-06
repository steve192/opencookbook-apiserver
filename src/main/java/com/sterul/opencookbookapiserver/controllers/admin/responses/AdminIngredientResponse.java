package com.sterul.opencookbookapiserver.controllers.admin.responses;

import java.time.Instant;
import java.util.List;

import com.sterul.opencookbookapiserver.entities.Ingredient;

public record AdminIngredientResponse(
        Long id,
        String name,
        String additionalInfo,
        boolean publicIngredient,
        Long ownerUserId,
        String ownerEmailAddress,
        Long aliasForId,
        String aliasForName,
        List<AlternativeName> alternativeNames,
        Float nutrientsEnergy,
        Float nutrientsFat,
        Float nutrientsSaturatedFat,
        Float nutrientsCarbohydrates,
        Float nutrientsSugar,
        Float nutrientsProtein,
        Float nutrientsSalt,
        Instant createdOn,
        Instant lastChange) {

    public record AlternativeName(Long id, String languageIsoCode, String alternativeName) {
    }

    public static AdminIngredientResponse fromEntity(Ingredient ingredient) {
        var owner = ingredient.getOwner();
        var aliasFor = ingredient.getAliasFor();
        return new AdminIngredientResponse(
                ingredient.getId(),
                ingredient.getName(),
                ingredient.getAdditionalInfo(),
                ingredient.isPublicIngredient(),
                owner == null ? null : owner.getUserId(),
                owner == null ? null : owner.getEmailAddress(),
                aliasFor == null ? null : aliasFor.getId(),
                aliasFor == null ? null : aliasFor.getName(),
                ingredient.getAlternativeNames().stream()
                        .map(name -> new AlternativeName(name.getId(), name.getLanguageIsoCode(),
                                name.getAlternativeName()))
                        .toList(),
                ingredient.getNutrientsEnergy(),
                ingredient.getNutrientsFat(),
                ingredient.getNutrientsSaturatedFat(),
                ingredient.getNutrientsCarbohydrates(),
                ingredient.getNutrientsSugar(),
                ingredient.getNutrientsProtein(),
                ingredient.getNutrientsSalt(),
                ingredient.getCreatedOn(),
                ingredient.getLastChange());
    }
}
