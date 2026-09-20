package com.sterul.opencookbookapiserver.services.suggestion;

import java.util.List;

import org.springframework.stereotype.Component;

import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.entities.recipe.Recipe;
import com.sterul.opencookbookapiserver.repositories.RecipeRepository;
import com.sterul.opencookbookapiserver.services.selection.MatchTarget;
import com.sterul.opencookbookapiserver.services.selection.RecipeMatcher;
import com.sterul.opencookbookapiserver.services.selection.RecipeSuitability;

/** Narrows a cookbook to the recipes a request could return at all. */
@Component
public class CandidatePool {

    private final RecipeRepository recipeRepository;
    private final RecipeMatcher matcher;

    public CandidatePool(RecipeRepository recipeRepository, RecipeMatcher matcher) {
        this.recipeRepository = recipeRepository;
        this.matcher = matcher;
    }

    /**
     * @param afterFilters recipes left by dish role, diet, time and meal, before the wanted ingredients
     * @param nearMisses   only ever filled for {@link MatchMode#MUST_CONTAIN}
     */
    public record Pool(int owned, int afterFilters, List<SuggestionCandidate> hits,
            List<SuggestionCandidate> nearMisses) {

        public Pool {
            hits = List.copyOf(hits);
            nearMisses = List.copyOf(nearMisses);
        }
    }

    public Pool of(CookpalUser owner, List<MatchTarget> targets, SuggestionCriteria criteria) {
        var owned = recipeRepository.findByOwnerWithIngredients(owner);
        var eligible = owned.stream().filter(recipe -> isEligible(recipe, criteria)).toList();
        var candidates = eligible.stream()
                .map(recipe -> new SuggestionCandidate(recipe, matcher.match(recipe, targets)))
                .toList();

        return partition(owned.size(), candidates, targets, criteria);
    }

    private static boolean isEligible(Recipe recipe, SuggestionCriteria criteria) {
        return RecipeSuitability.isServedOnItsOwn(recipe)
                && isWithinTime(recipe, criteria)
                && RecipeSuitability.suitsDiet(recipe, criteria.diet())
                && RecipeSuitability.suitsMeals(recipe, criteria.mealTypes());
    }

    /** A recipe with no time recorded stays in: hiding it for missing metadata would be worse. */
    private static boolean isWithinTime(Recipe recipe, SuggestionCriteria criteria) {
        if (criteria.maxTotalTimeMinutes() == null) {
            return true;
        }
        var minutes = recipe.minutesNeeded();
        return minutes == null || minutes <= criteria.maxTotalTimeMinutes();
    }

    private static Pool partition(int owned, List<SuggestionCandidate> candidates, List<MatchTarget> targets,
            SuggestionCriteria criteria) {
        if (targets.isEmpty()) {
            return new Pool(owned, candidates.size(), candidates, List.of());
        }
        return switch (criteria.mode()) {
            case ANY_RANKED -> new Pool(owned, candidates.size(),
                    candidates.stream().filter(candidate -> candidate.match().hasAny()).toList(), List.of());
            case MUST_CONTAIN -> mustContain(owned, candidates, targets);
        };
    }

    private static Pool mustContain(int owned, List<SuggestionCandidate> candidates, List<MatchTarget> targets) {
        var hits = candidates.stream().filter(candidate -> candidate.match().hasAll()).toList();
        return new Pool(owned, candidates.size(), hits, nearMisses(candidates, targets));
    }

    /**
     * A near miss needs at least two wanted ingredients: with one, "missing exactly one" is every
     * recipe that matches nothing at all.
     */
    private static List<SuggestionCandidate> nearMisses(List<SuggestionCandidate> candidates,
            List<MatchTarget> targets) {
        if (targets.size() < 2) {
            return List.of();
        }
        return candidates.stream().filter(candidate -> candidate.match().missing().size() == 1).toList();
    }
}
