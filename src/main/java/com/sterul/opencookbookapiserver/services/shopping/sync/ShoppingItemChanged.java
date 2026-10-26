package com.sterul.opencookbookapiserver.services.shopping.sync;

import java.time.Instant;
import java.util.List;

import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.entities.shopping.ItemChange;
import com.sterul.opencookbookapiserver.entities.shopping.ItemSource;
import com.sterul.opencookbookapiserver.entities.shopping.ShoppingItem;
import com.sterul.opencookbookapiserver.entities.shopping.ShoppingList;

/**
 * A person changed an item, as it stands afterwards. Published in the transaction, for people's ops only.
 *
 * @param spec         what was asked for when added, or the new amount
 * @param sources      the meals an addition was imported for
 * @param previousName before a rename
 */
public record ShoppingItemChanged(ShoppingList list, ShoppingItem item, ItemChange kind, Instant at, CookpalUser actor,
        String spec, List<ItemSource> sources, String previousName) {
}
