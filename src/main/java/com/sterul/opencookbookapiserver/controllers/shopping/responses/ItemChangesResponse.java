package com.sterul.opencookbookapiserver.controllers.shopping.responses;

import java.util.List;

import com.sterul.opencookbookapiserver.services.shopping.sync.ItemChanges;

/** @param full the items are the whole list and replace what the device has */
public record ItemChangesResponse(long version, boolean full, List<ShoppingItemResponse> items) {

    public static ItemChangesResponse of(ItemChanges changes) {
        return new ItemChangesResponse(changes.version(), changes.full(),
                changes.items().stream().map(ShoppingItemResponse::of).toList());
    }
}
