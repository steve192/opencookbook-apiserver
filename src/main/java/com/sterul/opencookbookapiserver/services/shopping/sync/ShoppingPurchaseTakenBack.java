package com.sterul.opencookbookapiserver.services.shopping.sync;

import java.time.Instant;

import com.sterul.opencookbookapiserver.entities.shopping.ShoppingList;

/** A tick was undone within a slip, so whatever purchase it recorded never happened. Published in the transaction. */
public record ShoppingPurchaseTakenBack(ShoppingList list, String nameKey, Instant boughtAt) {
}
