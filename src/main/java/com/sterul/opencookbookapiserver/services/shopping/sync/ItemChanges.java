package com.sterul.opencookbookapiserver.services.shopping.sync;

import java.util.List;

import com.sterul.opencookbookapiserver.entities.shopping.ShoppingItem;

/**
 * @param full whether the items are the whole list, to replace what the device has, rather than what changed
 * @param items with tombstones unless full
 */
public record ItemChanges(long version, boolean full, List<ShoppingItem> items) {
}
