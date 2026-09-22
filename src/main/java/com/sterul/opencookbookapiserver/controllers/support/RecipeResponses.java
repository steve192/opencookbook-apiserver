package com.sterul.opencookbookapiserver.controllers.support;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.sterul.opencookbookapiserver.controllers.responses.NutritionSummaryResponse;
import com.sterul.opencookbookapiserver.controllers.responses.RecipeResponse;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.entities.recipe.Recipe;

@Component
public class RecipeResponses {

    private final Optional<NutritionSummaries> summaries;

    public RecipeResponses(Optional<NutritionSummaries> summaries) {
        this.summaries = summaries;
    }

    public RecipeResponse of(Recipe recipe) {
        var response = RecipeResponse.fromEntity(recipe);
        response.setNutrition(nutritionOf(recipe));
        return response;
    }

    /** Adds whose recipe it is; a share or an unsaved import has no reader and leaves both null. */
    public RecipeResponse forReader(Recipe recipe, CookpalUser reader) {
        var response = of(recipe);
        var mine = recipe.isOwnedBy(reader);
        response.setMine(mine);
        response.setOwnerDisplayName(mine ? null : DisplayNames.of(recipe.getOwner()));
        return response;
    }

    /** Null while nutrition is off or the catalogue not ready, and for unsaved imports, which are not linked yet. */
    public NutritionSummaryResponse nutritionOf(Recipe recipe) {
        if (recipe.getId() == null) {
            return null;
        }
        return summaries.flatMap(nutrition -> nutrition.of(recipe)).orElse(null);
    }
}
