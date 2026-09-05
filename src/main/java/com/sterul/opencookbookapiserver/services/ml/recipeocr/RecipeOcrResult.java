package com.sterul.opencookbookapiserver.services.ml.recipeocr;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/** The subsystem's answer for a photographed recipe, as gson reads it. */
@Data
public class RecipeOcrResult {

    private String language;
    private String modelVersion;
    private int pageCount;
    private Field title;
    private Field servings;
    private Field preparationTime;
    private Field totalTime;
    private List<Ingredient> ingredients = new ArrayList<>();
    private List<Field> preparationSteps = new ArrayList<>();
    private Blocks blocks;
    private Photo photo;
    private String rawText;

    /**
     * An explicit null in the json overwrites the initialiser above - gson only leaves it alone
     * when the key is absent - and the subsystem sends null for "there is none of that in the
     * picture". Reading these through the accessors keeps that from being a null list.
     */
    public List<Ingredient> getIngredients() {
        return ingredients == null ? List.of() : ingredients;
    }

    public List<Field> getPreparationSteps() {
        return preparationSteps == null ? List.of() : preparationSteps;
    }

    /** What is wrong with the photograph, where anything is. */
    @Data
    public static class Photo {
        private boolean usable = true;
        private String problem;
        private Integer pageIndex;
    }

    /**
     * Where each kind of content was found, so the app can draw it over the photograph and ask
     * whether it is right.
     */
    @Data
    public static class Blocks {
        private List<Block> ingredients;
        private List<Block> steps;
    }

    @Data
    public static class Block {
        private int pageIndex;
        private int lineCount;
        private Box box;
    }

    /** A rectangle in fractions of the page, so it survives any scaling on the way. */
    @Data
    public static class Box {
        private double left;
        private double top;
        private double right;
        private double bottom;
    }

    /** A value the subsystem is only as sure of as it says. */
    @Data
    public static class Field {
        private Object value;
        private double confidence;

        public String asText() {
            return value == null ? null : String.valueOf(value);
        }

        public Integer asInteger() {
            if (value instanceof Number number) {
                return number.intValue();
            }
            return null;
        }
    }

    @Data
    public static class Ingredient {
        private String raw;
        private Double amount;
        private String unit;
        private String name;
        private String additionalInfo;
        private double confidence;
    }
}
