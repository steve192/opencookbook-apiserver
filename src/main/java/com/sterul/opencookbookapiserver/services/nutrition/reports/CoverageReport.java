package com.sterul.opencookbookapiserver.services.nutrition.reports;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sterul.opencookbookapiserver.configurations.nutrition.ConditionalOnNutritionEnabled;
import com.sterul.opencookbookapiserver.repositories.RecipeRepository;
import com.sterul.opencookbookapiserver.services.nutrition.IngredientNames;
import com.sterul.opencookbookapiserver.services.nutrition.calculation.LineNutrition;
import com.sterul.opencookbookapiserver.services.nutrition.calculation.LineStatus;
import com.sterul.opencookbookapiserver.services.nutrition.calculation.NutritionCalculator;
import com.sterul.opencookbookapiserver.services.nutrition.calculation.RecipeNutrition;

/** The calculator run over every recipe of this instance. */
@Service
@ConditionalOnNutritionEnabled
@Transactional(readOnly = true)
public class CoverageReport {

    private static final int TOP = 20;

    private final RecipeRepository recipeRepository;
    private final NutritionCalculator calculator;

    public CoverageReport(RecipeRepository recipeRepository, NutritionCalculator calculator) {
        this.recipeRepository = recipeRepository;
        this.calculator = calculator;
    }

    public record Count(String key, long count) {
    }

    /** @param warningCauses line statuses, or the flags of resolved lines; most common first */
    public record Coverage(int recipeCount, Map<RecipeNutrition.Status, Long> recipesByStatus, long lineCount, long warningLineCount,
            List<Count> warningCauses, List<Count> namesCausingWarnings) {
    }

    public Coverage coverage() {
        var nutrition = recipeRepository.findAllWithIngredients().stream().map(calculator::calculate).toList();
        var byStatus = new EnumMap<RecipeNutrition.Status, Long>(RecipeNutrition.Status.class);
        for (var status : RecipeNutrition.Status.values()) {
            byStatus.put(status, nutrition.stream().filter(recipe -> recipe.status() == status).count());
        }
        var lines = nutrition.stream().flatMap(recipe -> recipe.lines().stream()).toList();
        var warning = lines.stream().filter(LineNutrition::warns).toList();
        return new Coverage(nutrition.size(), byStatus, lines.size(), warning.size(),
                mostCommon(warning.stream().flatMap(CoverageReport::causes)),
                mostCommon(warning.stream().map(line -> IngredientNames.normalise(line.need().getIngredient().getName()))));
    }

    private static Stream<String> causes(LineNutrition line) {
        return line.status() == LineStatus.RESOLVED
                ? line.flags().stream().map(Enum::name)
                : Stream.of(line.status().name());
    }

    private static List<Count> mostCommon(Stream<String> keys) {
        return keys.collect(Collectors.groupingBy(Function.identity(), Collectors.counting())).entrySet().stream()
                .map(entry -> new Count(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingLong(Count::count).reversed().thenComparing(Count::key))
                .limit(TOP)
                .toList();
    }
}
