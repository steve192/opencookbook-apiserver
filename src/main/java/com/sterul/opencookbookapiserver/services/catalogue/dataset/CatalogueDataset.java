package com.sterul.opencookbookapiserver.services.catalogue.dataset;

import java.util.List;
import java.util.Map;

import com.sterul.opencookbookapiserver.entities.catalogue.Aisle;

/** Records mirroring the generated JSON files in src/main/resources/catalogue. */
public final class CatalogueDataset {

    private CatalogueDataset() {
    }

    public record Manifest(String label, String checksum, List<String> files, List<String> languages, int foods,
            List<Attribution> attributions, List<SourceRelease> sources) {
    }

    public record Attribution(String source, String name, String homepage, String text, String license, String licenseUrl) {
    }

    public record SourceRelease(String source, String release, String file, String sha256) {
    }

    public record Catalogue(List<Food> foods) {
    }

    /**
     * @param dietClass VEGAN, VEGETARIAN or MEAT; see catalogue-data/curation/diet-classes.yaml
     * @param icon a Fluent Emoji name; null shows the aisle's
     */
    public record Food(String key, String variantOf, Source source, List<String> states, boolean negligible,
            String dietClass, Float densityGPerMl, Nutrients nutrients, List<Name> names, List<Portion> portions,
            Aisle aisle, boolean shoppingTile, String icon) {

        public Food {
            states = states == null ? List.of() : states;
            names = names == null ? List.of() : names;
            portions = portions == null ? List.of() : portions;
        }
    }

    public record Source(String type, String code, String name) {
    }

    public record NonFoodItems(List<NonFoodItem> items) {
    }

    /** Sold next to food, and named by no food: toilet paper, detergent. */
    public record NonFoodItem(String key, Aisle aisle, String icon, List<Name> names) {
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

    /**
     * @param sparingUses  phrases for ingredients used only a little ("für die Form")
     * @param descriptions words saying how a food is cut or served ("gehackt"), not what it is
     */
    public record Lexicon(String language, Map<String, List<String>> units, Map<String, List<String>> states,
            List<String> preparationContext, List<String> sparingUses, List<String> descriptions) {

        public Lexicon {
            units = units == null ? Map.of() : units;
            states = states == null ? Map.of() : states;
            preparationContext = preparationContext == null ? List.of() : preparationContext;
            sparingUses = sparingUses == null ? List.of() : sparingUses;
            descriptions = descriptions == null ? List.of() : descriptions;
        }
    }
}
