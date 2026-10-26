package com.sterul.opencookbookapiserver.controllers.requests;

import com.sterul.opencookbookapiserver.entities.shopping.ShoppingProvider;

/**
 * A missing field stays as it is.
 *
 * @param suggestions whether the app shows suggestions of regularly bought items
 */
public record ShoppingSettingsRequest(ShoppingProvider provider, Boolean suggestions) {
}
