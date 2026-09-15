package com.sterul.opencookbookapiserver.services.nutrition.linking;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.sterul.opencookbookapiserver.entities.nutrition.CatalogueNameRule;
import com.sterul.opencookbookapiserver.services.nutrition.IngredientNames;

/** Name rules indexed for lookup by normalised name. */
public final class RuleBook {

    private final Set<String> notFoods = new HashSet<>();
    private final Map<String, Set<String>> forbiddenFoodKeys = new HashMap<>();

    public RuleBook(Collection<CatalogueNameRule> rules) {
        for (var rule : rules) {
            if (rule.getKind() == CatalogueNameRule.Kind.NOT_A_FOOD) {
                notFoods.add(rule.getName());
            } else {
                forbiddenFoodKeys.computeIfAbsent(rule.getName(), name -> new HashSet<>())
                        .add(rule.getCatalogueFood().getCatalogueKey());
            }
        }
    }

    public boolean isNotAFood(String name) {
        return notFoods.contains(IngredientNames.normalise(name));
    }

    public boolean forbids(String name, String foodKey) {
        return forbiddenFoodKeys.getOrDefault(IngredientNames.normalise(name), Set.of()).contains(foodKey);
    }
}
