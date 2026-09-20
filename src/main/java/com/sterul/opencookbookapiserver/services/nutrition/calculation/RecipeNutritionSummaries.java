package com.sterul.opencookbookapiserver.services.nutrition.calculation;

import java.time.Clock;
import java.util.Comparator;
import java.util.Optional;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sterul.opencookbookapiserver.configurations.nutrition.ConditionalOnNutritionEnabled;
import com.sterul.opencookbookapiserver.entities.nutrition.CatalogueFood;
import com.sterul.opencookbookapiserver.entities.nutrition.NutritionQuality;
import com.sterul.opencookbookapiserver.entities.nutrition.RecipeNutritionSummary;
import com.sterul.opencookbookapiserver.entities.recipe.Recipe;
import com.sterul.opencookbookapiserver.repositories.RecipeNutritionSummaryRepository;
import com.sterul.opencookbookapiserver.services.RecipeChangedEvent;
import com.sterul.opencookbookapiserver.services.nutrition.catalogue.CatalogueChangedEvent;
import com.sterul.opencookbookapiserver.services.nutrition.dataset.NutritionDatasetReader;

import lombok.extern.slf4j.Slf4j;

/**
 * A cache of {@link NutritionCalculator} results for ranking whole cookbooks. Rows are never edited:
 * any change to a recipe or the catalogue drops them, and the next read recomputes.
 */
@Service
@Slf4j
@Transactional
@ConditionalOnNutritionEnabled
public class RecipeNutritionSummaries {

    private final RecipeNutritionSummaryRepository summaryRepository;
    private final NutritionCalculator calculator;
    private final NutritionDatasetReader dataset;
    private final Clock clock;

    public RecipeNutritionSummaries(RecipeNutritionSummaryRepository summaryRepository,
            NutritionCalculator calculator, NutritionDatasetReader dataset, Clock clock) {
        this.summaryRepository = summaryRepository;
        this.calculator = calculator;
        this.dataset = dataset;
        this.clock = clock;
    }

    /** The stored summary, computed and stored now if it is missing or was left by an older dataset. */
    public RecipeNutritionSummary of(Recipe recipe) {
        return summaryRepository.findById(recipe.getId())
                .filter(this::isCurrent)
                .orElseGet(() -> summaryRepository.save(compute(recipe)));
    }

    @EventListener
    public void forgetAfter(RecipeChangedEvent change) {
        summaryRepository.deleteById(change.recipeId());
    }

    /** Any catalogue change can move any recipe; finding the affected ones costs more than recomputing. */
    @EventListener
    public void forgetEverythingAfter(CatalogueChangedEvent change) {
        var dropped = summaryRepository.deleteAllSummaries();
        log.info("Dropped {} nutrition summaries: {}", dropped, change.reason());
    }

    /** A row from an older dataset release describes foods that may since have changed. */
    private boolean isCurrent(RecipeNutritionSummary summary) {
        return dataset.manifest().label().equals(summary.getDatasetLabel());
    }

    private RecipeNutritionSummary compute(Recipe recipe) {
        var nutrition = calculator.calculate(recipe);
        var main = dominantFood(nutrition);
        return RecipeNutritionSummary.builder()
                .recipe(recipe)
                .perServing(nutrition.values().toValues())
                .perServingBasis(nutrition.basis() == RecipeNutrition.Basis.SERVING)
                .quality(qualityOf(nutrition))
                .warningCount(nutrition.warningCount())
                .mainFood(main.map(DominantFood::food).orElse(null))
                .mainFoodGramShare(main.map(DominantFood::share).orElse(null))
                .computedAt(clock.instant())
                .datasetLabel(dataset.manifest().label())
                .build();
    }

    private record DominantFood(CatalogueFood food, float share) {
    }

    /** The food making up the most grams, counting only weighed lines: what "another pasta dish" means. */
    private static Optional<DominantFood> dominantFood(RecipeNutrition nutrition) {
        var weighed = nutrition.lines().stream()
                .filter(line -> line.grams() != null && line.need().getIngredient() != null
                        && line.need().getIngredient().getCatalogueFood() != null)
                .toList();
        var total = weighed.stream().mapToDouble(LineNutrition::grams).sum();
        if (total <= 0) {
            return Optional.empty();
        }
        return weighed.stream()
                .max(Comparator.comparingDouble(LineNutrition::grams))
                .map(line -> new DominantFood(line.need().getIngredient().getCatalogueFood(),
                        (float) (line.grams() / total)));
    }

    private static NutritionQuality qualityOf(RecipeNutrition nutrition) {
        return switch (nutrition.status()) {
            case COMPLETE -> NutritionQuality.COMPLETE;
            case INCOMPLETE -> NutritionQuality.INCOMPLETE;
            case UNAVAILABLE -> NutritionQuality.UNAVAILABLE;
        };
    }
}
