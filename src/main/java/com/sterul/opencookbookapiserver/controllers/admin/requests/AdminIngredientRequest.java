package com.sterul.opencookbookapiserver.controllers.admin.requests;

import java.util.List;

import com.sterul.opencookbookapiserver.entities.IngredientAlternativeNames;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AdminIngredientRequest {
    private Long id;
    private String name;
    private List<IngredientAlternativeNames> alternativeNames;
    private Float nutrientsEnergy;
    private Float nutrientsFat;
    private Float nutrientsSaturatedFat;
    private Float nutrientsCarbohydrates;
    private Float nutrientsSugar;
    private Float nutrientsProtein;
    private Float nutrientsSalt;

}
