package com.sterul.opencookbookapiserver.services.nutrition.dataset;

import java.util.List;
import java.util.Map;

/** Records mirroring the generated JSON files in src/main/resources/nutrition. */
public final class NutritionDataset {

    private NutritionDataset() {
    }

    public record Manifest(String label, String checksum, List<String> files, List<String> languages, int foods,
            List<Attribution> attributions, List<SourceRelease> sources) {
    }

    public record Attribution(String source, String text, String license, String licenseUrl) {
    }

    public record SourceRelease(String source, String release, String file, String sha256) {
    }

    public record Catalogue(List<Food> foods) {
    }

    public record Food(String key, String variantOf, Source source, List<String> states, boolean negligible,
            Float densityGPerMl, Nutrients nutrients, List<Name> names, List<Portion> portions) {

        public Food {
            states = states == null ? List.of() : states;
            names = names == null ? List.of() : names;
            portions = portions == null ? List.of() : portions;
        }
    }

    public record Source(String type, String code, String name) {
    }

    public record Nutrients(Float energyKcal, Float energyKj, Float fat, Float saturatedFat, Float carbohydrates,
            Float sugar, Float fibre, Float protein, Float salt) {
    }

    public record Name(String language, String name, boolean display) {
    }

    public record Portion(String unit, float grams, String origin) {
    }

    public record Units(List<Unit> units) {
    }

    /** @param typicalGrams what a container usually holds, for foods without an own portion */
    public record Unit(String key, UnitKind kind, Float grams, Float millilitres, String sizeOf, Float factor,
            Float typicalGrams, Float typicalMillilitres) {
    }

    public enum UnitKind {
        MASS, VOLUME, COUNT, PINCH, VAGUE
    }

    public record States(List<State> states) {
    }

    public record State(String key, String kind, boolean unprepared) {
    }

    public record Lexicons(List<Lexicon> lexicons) {
    }

    /** @param sparingUses phrases for ingredients used only a little ("für die Form") */
    public record Lexicon(String language, Map<String, List<String>> units, Map<String, List<String>> states,
            List<String> preparationContext, List<String> sparingUses) {

        public Lexicon {
            units = units == null ? Map.of() : units;
            states = states == null ? Map.of() : states;
            preparationContext = preparationContext == null ? List.of() : preparationContext;
            sparingUses = sparingUses == null ? List.of() : sparingUses;
        }
    }
}
