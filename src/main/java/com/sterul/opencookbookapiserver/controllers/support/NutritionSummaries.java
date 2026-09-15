package com.sterul.opencookbookapiserver.controllers.support;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.sterul.opencookbookapiserver.configurations.nutrition.ConditionalOnNutritionEnabled;
import com.sterul.opencookbookapiserver.controllers.responses.NutritionSummaryResponse;
import com.sterul.opencookbookapiserver.entities.recipe.Recipe;
import com.sterul.opencookbookapiserver.services.nutrition.calculation.NutritionCalculator;
import com.sterul.opencookbookapiserver.services.nutrition.matching.CatalogueMatcher;

/** Empty until the catalogue is ready, so recipes don't warn about ingredients about to be matched. */
@Component
@ConditionalOnNutritionEnabled
public class NutritionSummaries {

    private final NutritionCalculator calculator;
    private final CatalogueMatcher matcher;

    public NutritionSummaries(NutritionCalculator calculator, CatalogueMatcher matcher) {
        this.calculator = calculator;
        this.matcher = matcher;
    }

    public Optional<NutritionSummaryResponse> of(Recipe recipe) {
        if (!matcher.isReady()) {
            return Optional.empty();
        }
        return Optional.of(NutritionSummaryResponse.of(calculator.calculate(recipe)));
    }
}
