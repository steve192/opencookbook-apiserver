package com.sterul.opencookbookapiserver.controllers.shopping.responses;

import java.time.Instant;

import com.sterul.opencookbookapiserver.entities.catalogue.Aisle;
import com.sterul.opencookbookapiserver.entities.shopping.ShoppingListEvent;

/** A name the list held lately, as it was last held. */
public record ShoppingEntryResponse(String name, Aisle aisle, String icon, Instant lastUsedAt) {

    public static ShoppingEntryResponse of(ShoppingListEvent added) {
        return new ShoppingEntryResponse(added.getName(), added.getAisle(), added.getIcon(), added.getAt());
    }
}
