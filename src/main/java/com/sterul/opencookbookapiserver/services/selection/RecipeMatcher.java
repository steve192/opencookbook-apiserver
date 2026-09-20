package com.sterul.opencookbookapiserver.services.selection;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.sterul.opencookbookapiserver.entities.Ingredient;
import com.sterul.opencookbookapiserver.entities.IngredientNeed;
import com.sterul.opencookbookapiserver.entities.recipe.Recipe;

/** Decides which of the wanted ingredients a recipe has. */
@Component
public class RecipeMatcher {

    public MatchResult match(Recipe recipe, List<MatchTarget> targets) {
        var ingredients = recipe.getNeededIngredients().stream()
                .map(IngredientNeed::getIngredient)
                .filter(Objects::nonNull)
                .toList();

        var byPresence = targets.stream()
                .collect(Collectors.partitioningBy(target -> ingredients.stream().anyMatch(target::matches)));
        var matchedLines = ingredients.stream().filter(ingredient -> matchesAny(ingredient, targets)).count();

        return new MatchResult(byPresence.get(true), byPresence.get(false), (int) matchedLines, ingredients.size());
    }

    private static boolean matchesAny(Ingredient ingredient, List<MatchTarget> targets) {
        return targets.stream().anyMatch(target -> target.matches(ingredient));
    }
}
