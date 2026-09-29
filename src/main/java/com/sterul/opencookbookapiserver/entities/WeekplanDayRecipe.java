package com.sterul.opencookbookapiserver.entities;

import java.time.LocalDate;

import org.hibernate.annotations.UuidGenerator;
import org.springframework.lang.Nullable;

import com.sterul.opencookbookapiserver.entities.recipe.Recipe;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeekplanDayRecipe {

    @Id
    @UuidGenerator
    private String id;

    @Nullable
    @ManyToOne
    private Recipe recipe;

    private boolean isSimpleRecipe;

    private String simpleRecipeText;

    /** How many servings are cooked; null for leftovers and meals without a recipe. */
    private Integer servings;

    /** The day the recipe was cooked whose leftovers this meal eats; null for a meal cooked on its own day. */
    private LocalDate leftoverOf;

    /** Null servings: as the recipe is written. */
    public static WeekplanDayRecipe cooked(Recipe recipe, @Nullable Integer servings) {
        return builder().isSimpleRecipe(false).recipe(recipe)
                .servings(servings != null ? servings : recipe.writtenServings()).build();
    }

    public static WeekplanDayRecipe leftover(Recipe recipe, LocalDate cookedOn) {
        return builder().isSimpleRecipe(false).recipe(recipe).leftoverOf(cookedOn).build();
    }

    public static WeekplanDayRecipe simple(String text) {
        return builder().isSimpleRecipe(true).simpleRecipeText(text).build();
    }

}
