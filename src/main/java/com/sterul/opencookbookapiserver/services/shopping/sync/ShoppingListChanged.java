package com.sterul.opencookbookapiserver.services.shopping.sync;

/** Items of a list changed; published inside the changing transaction. */
public record ShoppingListChanged(Long listId, long version) {
}
