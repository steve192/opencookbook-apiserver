package com.sterul.opencookbookapiserver.controllers.support;

import org.springframework.stereotype.Component;

import com.sterul.opencookbookapiserver.controllers.responses.NutritionSummaryResponse;
import com.sterul.opencookbookapiserver.controllers.responses.RecipeResponse;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.entities.recipe.Recipe;
import com.sterul.opencookbookapiserver.services.ReadableRecipe;

@Component
public class RecipeResponses {

    private final NutritionSummaries summaries;

    public RecipeResponses(NutritionSummaries summaries) {
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

    public RecipeResponse inCookbookOf(ReadableRecipe readable, CookpalUser reader) {
        var response = forReader(readable.recipe(), reader);
        response.setHouseholdIds(readable.householdIds());
        return response;
    }

    /** Null while the catalogue is not ready, and for unsaved imports, which are not linked yet. */
    public NutritionSummaryResponse nutritionOf(Recipe recipe) {
        if (recipe.getId() == null) {
            return null;
        }
        return summaries.of(recipe).orElse(null);
    }
}
