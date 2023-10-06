package com.sterul.opencookbookapiserver.controllers.responses;

import java.util.HashMap;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class IngredientNutrientInfoResponse {
    private Long id;

    @Builder.Default
    private Map<String, Float> energyPerUnit = new HashMap<>();
}


