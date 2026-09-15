package com.sterul.opencookbookapiserver.services.nutrition.calculation;

import java.util.EnumSet;
import java.util.List;

import org.springframework.stereotype.Service;

import com.sterul.opencookbookapiserver.configurations.nutrition.ConditionalOnNutritionEnabled;
import com.sterul.opencookbookapiserver.entities.Ingredient;
import com.sterul.opencookbookapiserver.entities.IngredientNeed;
import com.sterul.opencookbookapiserver.entities.nutrition.CatalogueFood;
import com.sterul.opencookbookapiserver.entities.recipe.Recipe;
import com.sterul.opencookbookapiserver.services.nutrition.matching.ConfidenceBand;

/**
 * Estimates a recipe's nutrients on demand; never stored. A line warns by impact:
 * - unknown unit or piece weight, unless the food is negligible;
 * - unlinked, if even as rich as fat it could be a noticeable share, or its amount is unmeasurable;
 * - no amount on a rich food ("etwas Öl");
 * - uncertain and a noticeable share of the energy.
 * Lines used only a little ("Mehl für die Form") never warn for being unlinked or without amount.
 */
@Service
@ConditionalOnNutritionEnabled
public class NutritionCalculator {

    static final double RICH_KCAL_PER_100_G = 100;
    static final double NOTICEABLE_SHARE = 0.10;
    /** Pure fat: the most an unknown food can contain. */
    static final double RICHEST_KCAL_PER_GRAM = 9.0;

    private final GramsResolver gramsResolver;
    private final SparingUses sparingUses;

    public NutritionCalculator(GramsResolver gramsResolver, SparingUses sparingUses) {
        this.gramsResolver = gramsResolver;
        this.sparingUses = sparingUses;
    }

    public RecipeNutrition calculate(Recipe recipe) {
        var provisional = recipe.getNeededIngredients().stream().map(this::line).toList();
        var total = provisional.stream().map(LineNutrition::values).reduce(NutrientAmounts.NONE, NutrientAmounts::plus);
        var lines = provisional.stream().map(line -> byImpact(line, total.energyKcal())).toList();
        var warnings = (int) lines.stream().filter(LineNutrition::warns).count();
        var servings = recipe.getServings();
        return new RecipeNutrition(
                servings > 0 ? RecipeNutrition.Basis.SERVING : RecipeNutrition.Basis.RECIPE,
                status(lines, warnings),
                servings > 0 ? total.dividedBy(servings) : total,
                warnings,
                lines);
    }

    private LineNutrition byImpact(LineNutrition line, double recipeKcal) {
        if (line.status() == LineStatus.RESOLVED && !line.flags().isEmpty()) {
            return withWarning(line, recipeKcal > 0 && line.values().energyKcal() >= NOTICEABLE_SHARE * recipeKcal);
        }
        if (line.status() == LineStatus.UNLINKED && line.warns()) {
            var need = line.need();
            return gramsResolver.weightWithoutFood(need.getAmount(), need.getUnit())
                    .map(grams -> withWarning(line, recipeKcal <= 0 || grams * RICHEST_KCAL_PER_GRAM >= NOTICEABLE_SHARE * recipeKcal))
                    .orElse(line);
        }
        return line;
    }

    private LineNutrition line(IngredientNeed need) {
        var ingredient = need.getIngredient();
        if (ingredient.isExcludedFromNutrition()) {
            return unresolved(need, LineStatus.EXCLUDED, false);
        }
        var food = ingredient.getCatalogueFood();
        var sparing = sparingUses.isSparing(ingredient.getName());
        if (food == null) {
            return unresolved(need, LineStatus.UNLINKED, !sparing);
        }
        var amount = gramsResolver.resolve(need.getAmount(), need.getUnit(), food, ingredient.getPortionOverrides());
        return switch (amount.status()) {
            case RESOLVED -> {
                var flags = EnumSet.noneOf(LineFlag.class);
                flags.addAll(amount.flags());
                if (isUncertainLink(ingredient)) {
                    flags.add(LineFlag.LOW_CONFIDENCE);
                }
                yield new LineNutrition(need, LineStatus.RESOLVED, amount.grams(),
                        NutrientAmounts.of(food.getNutrients(), amount.grams()), flags, amount.ownPortion(), false);
            }
            case NO_AMOUNT -> unresolved(need, LineStatus.NO_AMOUNT, !food.isNegligible() && isRich(food) && !sparing);
            default -> unresolved(need, amount.status(), !food.isNegligible());
        };
    }

    /** UNAVAILABLE when at least half of the lines that matter (resolved or warning) warn. */
    private static RecipeNutrition.Status status(List<LineNutrition> lines, int warnings) {
        var mattering = lines.stream().filter(line -> line.status() == LineStatus.RESOLVED || line.warns()).count();
        if (mattering == 0 || warnings * 2 >= mattering) {
            return RecipeNutrition.Status.UNAVAILABLE;
        }
        return warnings == 0 ? RecipeNutrition.Status.COMPLETE : RecipeNutrition.Status.INCOMPLETE;
    }

    private static boolean isUncertainLink(Ingredient ingredient) {
        return ingredient.getLinkSource() == Ingredient.LinkSource.AUTO && ingredient.getLinkConfidence() != null
                && ConfidenceBand.of(ingredient.getLinkConfidence()) != ConfidenceBand.SILENT;
    }

    private static boolean isRich(CatalogueFood food) {
        var energy = food.getNutrients().getEnergyKcal();
        return energy != null && energy >= RICH_KCAL_PER_100_G;
    }

    private static LineNutrition unresolved(IngredientNeed need, LineStatus status, boolean warns) {
        return new LineNutrition(need, status, null, NutrientAmounts.NONE, EnumSet.noneOf(LineFlag.class), false, warns);
    }

    private static LineNutrition withWarning(LineNutrition line, boolean warns) {
        return new LineNutrition(line.need(), line.status(), line.grams(), line.values(), line.flags(), line.ownPortion(), warns);
    }
}
