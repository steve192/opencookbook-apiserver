package com.sterul.opencookbookapiserver.controllers.shopping.responses;

import java.time.LocalDate;
import java.util.List;

import com.sterul.opencookbookapiserver.services.shopping.preview.PreviewLine;
import com.sterul.opencookbookapiserver.services.shopping.preview.PreviewMeal;

/** The meals an import sheet offers, each with its ingredients as the recipe states them. */
public record ImportPreviewResponse(List<Meal> meals) {

    public record Meal(String entryId, LocalDate date, String title, Long recipeId, boolean spontaneous,
            int recipeServings, int defaultServings, LocalDate leftoverOf, List<PreviewLine> lines) {

        static Meal of(PreviewMeal meal) {
            return new Meal(meal.entryId(), meal.date(), meal.title(), meal.recipeId(), meal.isSpontaneous(),
                    meal.recipeServings(), meal.defaultServings(), meal.leftoverOf(), meal.lines());
        }
    }

    public static ImportPreviewResponse of(List<PreviewMeal> meals) {
        return new ImportPreviewResponse(meals.stream().map(Meal::of).toList());
    }
}
