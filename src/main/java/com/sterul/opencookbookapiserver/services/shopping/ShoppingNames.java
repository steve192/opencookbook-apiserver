package com.sterul.opencookbookapiserver.services.shopping;

import com.sterul.opencookbookapiserver.services.catalogue.IngredientNames;

/** How item names are compared: one item per key on a list, one staple per key for a person. */
public final class ShoppingNames {

    private ShoppingNames() {
    }

    public static String key(String name) {
        return IngredientNames.normalise(name);
    }
}
