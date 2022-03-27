package com.sterul.opencookbookapiserver.controllers.requests;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Data
public class WeekplanDayPut {
    private List<Recipe> recipes;

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = SimpleRecipe.class, name = "SIMPLE_RECIPE"),
            @JsonSubTypes.Type(value = NormalRecipe.class, name = "NORMAL_RECIPE")
    })

    @Setter
    @Getter
    public abstract static class Recipe {
        private final RecipeType type;

        protected Recipe(RecipeType type) {
            this.type = type;
        }

        public enum RecipeType {
            SIMPLE_RECIPE,
            NORMAL_RECIPE
        }
    }

    @Setter
    @Getter
    public static class SimpleRecipe extends Recipe {

        private String id;
        private String title;

        SimpleRecipe() {
            super(RecipeType.SIMPLE_RECIPE);
        }
    }

    @Setter
    @Getter
    public static class NormalRecipe extends Recipe {

        private Long id;

        NormalRecipe() {
            super(RecipeType.NORMAL_RECIPE);
        }
    }
}


