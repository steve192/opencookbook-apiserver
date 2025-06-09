package com.sterul.opencookbookapiserver.controllers.responses;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class IngredientResponse {
    private Long id;
    private String name;
    private String additionalInfo;

    private Float nutrientsEnergy;
    private Float nutrientsFat;
    private Float nutrientsSaturatedFat;
    private Float nutrientsCarbohydrates;
    private Float nutrientsSugar;
    private Float nutrientsProtein;
    private Float nutrientsSalt;
}
