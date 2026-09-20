package com.sterul.opencookbookapiserver.controllers.responses;

import java.util.List;
import java.util.function.Function;

import com.sterul.opencookbookapiserver.entities.recipe.Recipe;
import com.sterul.opencookbookapiserver.services.selection.MatchTarget;
import com.sterul.opencookbookapiserver.services.suggestion.RankedSuggestion;
import com.sterul.opencookbookapiserver.services.suggestion.SuggestionResult;

/** @param nearMisses recipes one wanted ingredient short, never mixed into the results */
public record RecipeSuggestionResponse(long seed, List<Suggestion> results, List<Suggestion> nearMisses,
        PoolStats poolStats) {

    public record Suggestion(RecipeResponse recipe, double score, List<IngredientReference> matchedIngredients,
            List<IngredientReference> missingIngredients, List<ScoreReason> reasons) {
    }

    public record IngredientReference(Long id, String name) {

        static IngredientReference fromTarget(MatchTarget target) {
            return new IngredientReference(target.ingredientId(), target.label());
        }
    }

    public record PoolStats(int owned, int afterFilters, int matched, int returned) {

        static PoolStats fromStats(SuggestionResult.PoolStats stats) {
            return new PoolStats(stats.owned(), stats.afterFilters(), stats.matched(), stats.returned());
        }
    }

    public static RecipeSuggestionResponse fromResult(SuggestionResult result, Function<Recipe, RecipeResponse> recipes) {
        return new RecipeSuggestionResponse(
                result.seed(),
                result.results().stream().map(suggestion -> toSuggestion(suggestion, recipes)).toList(),
                result.nearMisses().stream().map(suggestion -> toSuggestion(suggestion, recipes)).toList(),
                PoolStats.fromStats(result.poolStats()));
    }

    private static Suggestion toSuggestion(RankedSuggestion suggestion, Function<Recipe, RecipeResponse> recipes) {
        var match = suggestion.candidate().match();
        return new Suggestion(
                recipes.apply(suggestion.recipe()),
                suggestion.score(),
                match.matched().stream().map(IngredientReference::fromTarget).toList(),
                match.missing().stream().map(IngredientReference::fromTarget).toList(),
                suggestion.terms().stream().map(ScoreReason::of).toList());
    }
}
