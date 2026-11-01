package com.sterul.opencookbookapiserver.services.catalogue.linking;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.sterul.opencookbookapiserver.entities.catalogue.CatalogueNameRule;
import com.sterul.opencookbookapiserver.services.catalogue.IngredientNames;

/** Name rules indexed for lookup by normalised name. */
public final class RuleBook {

    private final Set<String> notFoods = new HashSet<>();
    private final Map<String, Set<String>> forbiddenFoodKeys = new HashMap<>();
    private final Set<String> keptAsWritten = new HashSet<>();

    public RuleBook(Collection<CatalogueNameRule> rules) {
        for (var rule : rules) {
            switch (rule.getKind()) {
                case NOT_A_FOOD -> notFoods.add(rule.getName());
                case NEVER_LINK_TO -> forbiddenFoodKeys.computeIfAbsent(rule.getName(), name -> new HashSet<>())
                        .add(rule.getCatalogueFood().getCatalogueKey());
                case KEEP_AS_WRITTEN -> keptAsWritten.add(rule.getName());
            }
        }
    }

    public boolean isNotAFood(String name) {
        return notFoods.contains(IngredientNames.normalise(name));
    }

    public boolean forbids(String name, String foodKey) {
        return forbiddenFoodKeys.getOrDefault(IngredientNames.normalise(name), Set.of()).contains(foodKey);
    }

    /** @param written a recipe line's name and note as written: "Salz, Pfeffer" */
    public boolean keepsAsWritten(String written) {
        return keptAsWritten.contains(IngredientNames.normalise(written));
    }
}
