package com.sterul.opencookbookapiserver.services.planning;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.sterul.opencookbookapiserver.entities.Ingredient;
import com.sterul.opencookbookapiserver.entities.IngredientNeed;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.entities.planning.PantryItem;
import com.sterul.opencookbookapiserver.entities.planning.PlanningProfile;
import com.sterul.opencookbookapiserver.entities.recipe.Recipe;
import com.sterul.opencookbookapiserver.repositories.IngredientRepository;
import com.sterul.opencookbookapiserver.repositories.RecipeRepository;
import com.sterul.opencookbookapiserver.services.nutrition.calculation.GramsResolver;
import com.sterul.opencookbookapiserver.services.nutrition.calculation.RecipeNutritionSummaries;
import com.sterul.opencookbookapiserver.services.selection.IngredientTargetResolver;
import com.sterul.opencookbookapiserver.services.selection.MatchTarget;
import com.sterul.opencookbookapiserver.services.selection.RecipeMatcher;
import com.sterul.opencookbookapiserver.services.selection.RecipeSuitability;

/**
 * The recipes a plan may use. Only dishes, the diet and avoided ingredients are hard filters; everything
 * else is scored, so a real cookbook yields a plan with a named compromise rather than an empty week.
 */
@Component
public class PlanningPool {

    private final RecipeRepository recipeRepository;
    private final IngredientRepository ingredientRepository;
    private final IngredientTargetResolver targetResolver;
    private final RecipeMatcher matcher;
    private final GramsResolver gramsResolver;
    private final Optional<RecipeNutritionSummaries> summaries;

    public PlanningPool(RecipeRepository recipeRepository, IngredientRepository ingredientRepository,
            IngredientTargetResolver targetResolver, RecipeMatcher matcher, GramsResolver gramsResolver,
            Optional<RecipeNutritionSummaries> summaries) {
        this.recipeRepository = recipeRepository;
        this.ingredientRepository = ingredientRepository;
        this.targetResolver = targetResolver;
        this.matcher = matcher;
        this.gramsResolver = gramsResolver;
        this.summaries = summaries;
    }

    public record Pool(List<PlanCandidate> candidates, PantryBudget pantry) {

        public Pool {
            candidates = List.copyOf(candidates);
        }
    }

    public Pool of(CookpalUser owner, PlanningProfile profile) {
        var avoided = targetResolver.resolve(List.copyOf(profile.getAvoidedIngredientIds()), owner);
        var allowed = recipeRepository.findByOwnerWithIngredients(owner).stream()
                .filter(RecipeSuitability::isServedOnItsOwn)
                .filter(recipe -> RecipeSuitability.suitsDiet(recipe, profile.getDiet()))
                .filter(recipe -> isFreeOf(recipe, avoided))
                .toList();

        var pantry = pantryBudget(owner, profile.getPantry());
        var effort = EffortScale.rank(allowed);
        var candidates = allowed.stream()
                .map(recipe -> new PlanCandidate(recipe, summaries.map(cache -> cache.of(recipe)).orElse(null),
                        effort.get(recipe.getId()), pantryUse(recipe, pantry)))
                .toList();
        return new Pool(candidates, pantry);
    }

    /**
     * Free of every avoided ingredient - and, where any are avoided, provably so. A recipe with an
     * unlinked ingredient cannot be shown to be free of an allergen, and guessing is the one thing
     * this filter may never do.
     */
    private boolean isFreeOf(Recipe recipe, List<MatchTarget> avoided) {
        if (avoided.isEmpty()) {
            return true;
        }
        return !matcher.match(recipe, avoided).hasAny() && recipe.getNeededIngredients().stream()
                .map(IngredientNeed::getIngredient)
                .filter(ingredient -> ingredient != null && !ingredient.isExcludedFromNutrition())
                .allMatch(ingredient -> ingredient.getCatalogueFood() != null);
    }

    private PantryBudget pantryBudget(CookpalUser owner, List<PantryItem> items) {
        if (items.isEmpty()) {
            return PantryBudget.EMPTY;
        }
        var ids = items.stream().map(PantryItem::getIngredientId).toList();
        var targets = targetResolver.resolve(ids, owner);
        var targetById = targets.stream().collect(Collectors.toMap(MatchTarget::ingredientId, Function.identity()));
        var ingredientById = ingredientRepository.findAllByIdInAndOwner(ids, owner).stream()
                .collect(Collectors.toMap(Ingredient::getId, Function.identity()));
        var initial = new HashMap<MatchTarget, Double>();
        var measured = new HashSet<MatchTarget>();
        for (var item : items) {
            var target = targetById.get(item.getIngredientId());
            if (target == null) {
                continue;
            }
            var grams = gramsResolver.gramsOf(item.getAmount(), item.getUnit(), ingredientById.get(item.getIngredientId()));
            grams.ifPresent(weight -> measured.add(target));
            initial.put(target, grams.orElse(1.0));
        }
        return new PantryBudget(initial, measured);
    }

    /** What one cooking of the recipe would take from each pantry item, in that item's measure. */
    private Map<MatchTarget, Double> pantryUse(Recipe recipe, PantryBudget pantry) {
        var use = new HashMap<MatchTarget, Double>();
        for (var target : pantry.items()) {
            var lines = recipe.getNeededIngredients().stream().filter(line -> target.matches(line.getIngredient())).toList();
            if (lines.isEmpty()) {
                continue;
            }
            // A line that cannot be weighed cannot be shown to use stock up, so it takes nothing measured.
            var amount = pantry.measured().contains(target) ?
                    lines.stream().map(line -> gramsResolver.gramsOf(line.getAmount(), line.getUnit(), line.getIngredient()))
                            .flatMap(Optional::stream).mapToDouble(Double::doubleValue).sum() :
                    1.0;
            if (amount > 0) {
                use.put(target, amount);
            }
        }
        return use;
    }
}
