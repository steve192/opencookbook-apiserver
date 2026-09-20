package com.sterul.opencookbookapiserver.controllers.admin.requests;

import java.util.List;

import com.sterul.opencookbookapiserver.controllers.responses.Nutrients;
import com.sterul.opencookbookapiserver.entities.nutrition.CatalogueFoodName;
import com.sterul.opencookbookapiserver.entities.nutrition.CatalogueFoodPortion;
import com.sterul.opencookbookapiserver.services.nutrition.catalogue.CatalogueService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Nutrients per 100 g. */
public record AdminCustomFoodRequest(
        @NotEmpty List<@Valid Name> names,
        @NotNull @Valid Nutrients nutrients,
        @Positive Float densityGPerMl,
        boolean negligible,
        List<@Valid Portion> portions) {

    public record Name(@NotBlank String languageIsoCode, @NotBlank String name, boolean display) {
    }

    public record Portion(@NotBlank String unitKey, @Positive float grams) {
    }

    public CatalogueService.CustomFood toCustomFood() {
        return new CatalogueService.CustomFood(
                names.stream().map(name -> CatalogueFoodName.builder().languageIsoCode(name.languageIsoCode())
                        .name(name.name()).display(name.display()).build()).toList(),
                nutrients.toValues(), densityGPerMl, negligible,
                portions == null ? List.of() : portions.stream()
                        .map(portion -> CatalogueFoodPortion.builder().unitKey(portion.unitKey()).grams(portion.grams()).build())
                        .toList());
    }
}
