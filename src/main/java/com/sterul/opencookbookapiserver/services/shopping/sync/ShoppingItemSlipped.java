package com.sterul.opencookbookapiserver.services.shopping.sync;

import java.time.Instant;

import com.sterul.opencookbookapiserver.entities.shopping.ShoppingList;

/**
 * An item was taken off within a slip of going on the list, so nothing since then happened, under whichever name.
 * Published in the transaction.
 */
public record ShoppingItemSlipped(ShoppingList list, String itemId, Instant listedAt) {
}
