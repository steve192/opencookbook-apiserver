package com.sterul.opencookbookapiserver.services.shopping.preview;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.sterul.opencookbookapiserver.entities.IngredientNeed;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.entities.recipe.Recipe;
import com.sterul.opencookbookapiserver.services.catalogue.UnitLexicon;
import com.sterul.opencookbookapiserver.services.shopping.ShoppingNames;
import com.sterul.opencookbookapiserver.services.shopping.placement.ItemPlacer;
import com.sterul.opencookbookapiserver.services.shopping.placement.PlacementQuery;

/** A recipe's ingredients as shopping lines, placed in their aisle. */
@Component
class PreviewLines {

    private final UnitLexicon unitLexicon;
    private final ItemPlacer placer;

    PreviewLines(UnitLexicon unitLexicon, ItemPlacer placer) {
        this.unitLexicon = unitLexicon;
        this.placer = placer;
    }

    List<PreviewLine> of(Recipe recipe, CookpalUser viewer, Set<String> stapleKeys) {
        return recipe.getNeededIngredients().stream()
                .filter(need -> need.getIngredient() != null)
                .map(need -> lineOf(need, viewer, stapleKeys))
                .filter(Objects::nonNull)
                .toList();
    }

    private PreviewLine lineOf(IngredientNeed need, CookpalUser viewer, Set<String> stapleKeys) {
        var ingredient = need.getIngredient();
        var key = ShoppingNames.key(ingredient.getName());
        if (key.isEmpty()) {
            return null;
        }
        var mergeUnit = MergeUnits.of(unitLexicon.resolve(need.getUnit()), need.getUnit());
        var placement = placer.place(new PlacementQuery(ingredient.getName(), viewer.getLanguage(),
                ingredient.getCatalogueFood()));
        return new PreviewLine(ingredient.getName(), key, need.getAmount(), need.getUnit(), mergeUnit.unit(),
                mergeUnit.factor(), placement.aisle(), placement.icon(), stapleKeys.contains(key));
    }
}
