package com.sterul.opencookbookapiserver.controllers.requests;

import java.util.List;
import java.util.Set;

import com.sterul.opencookbookapiserver.entities.recipe.Diet;
import com.sterul.opencookbookapiserver.entities.recipe.MacroStyle;
import com.sterul.opencookbookapiserver.entities.recipe.MealType;
import com.sterul.opencookbookapiserver.services.selection.Jitter;
import com.sterul.opencookbookapiserver.services.suggestion.MatchMode;
import com.sterul.opencookbookapiserver.services.suggestion.SuggestionCriteria;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Everything is optional: a request answering nothing is a valid "surprise me".
 *
 * @param seed repeat a previous seed to get that result set back; leave it out for a new draw
 * @param includeHouseholdRecipes also draw on the household cookbooks you may read; left out means no
 * @param inSeasonMonth also want what is in season in Germany in that month, 1 to 12; not with MUST_CONTAIN
 */
public record RecipeSuggestionRequest(
        MatchMode mode,
        @Size(max = MAX_INGREDIENTS) List<Long> ingredientIds,
        @Positive Long maxTotalTimeMinutes,
        Diet diet,
        Set<MealType> mealTypes,
        @Positive Integer targetKcalPerServing,
        MacroStyle macroStyle,
        Boolean includeHouseholdRecipes,
        @Min(1) @Max(12) Integer inSeasonMonth,
        @Min(1) @Max(MAX_LIMIT) Integer limit,
        Long seed) {

    public static final int MAX_INGREDIENTS = 20;
    public static final int MAX_LIMIT = 50;
    private static final int DEFAULT_LIMIT = 10;

    /** No recipe contains everything in season at once. */
    @AssertTrue(message = "inSeasonMonth does not combine with MUST_CONTAIN")
    @Schema(hidden = true)
    public boolean isSeasonWithRankedMatching() {
        return inSeasonMonth == null || mode != MatchMode.MUST_CONTAIN;
    }

    public SuggestionCriteria toCriteria() {
        return new SuggestionCriteria(mode, ingredientIds, maxTotalTimeMinutes, diet, mealTypes,
                targetKcalPerServing, macroStyle,
                Boolean.TRUE.equals(includeHouseholdRecipes),
                inSeasonMonth,
                limit == null ? DEFAULT_LIMIT : limit,
                seed == null ? Jitter.newSeed() : seed);
    }
}
