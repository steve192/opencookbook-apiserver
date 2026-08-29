package com.sterul.opencookbookapiserver.controllers.responses;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.time.LocalDate;
import java.util.List;

@Data
public class WeekplanDayResponse {

    private LocalDate day;
    
    private List<MinimalRecipe> recipes = new ArrayList<>();


    @Setter
    @Getter
    public abstract static class MinimalRecipe {
        private final RecipeType type;

        private String title;
        private String titleImageUuid;

        protected MinimalRecipe(RecipeType type) {
            this.type = type;
        }

        public enum RecipeType {
            SIMPLE_RECIPE,
            NORMAL_RECIPE
        }
    }

    @Setter
    @Getter
    public static class SimpleRecipe extends MinimalRecipe {

        private String id;

        public SimpleRecipe() {
            super(RecipeType.SIMPLE_RECIPE);
        }
    }

    @Setter
    @Getter
    public static class NormalRecipe extends MinimalRecipe {

        private Long id;

        public NormalRecipe() {
            super(RecipeType.NORMAL_RECIPE);
        }
    }

}
