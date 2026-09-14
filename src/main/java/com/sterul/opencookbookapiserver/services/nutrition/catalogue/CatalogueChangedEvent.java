package com.sterul.opencookbookapiserver.services.nutrition.catalogue;

/** Published within the changing transaction; {@code reason} is for the log. */
public record CatalogueChangedEvent(String reason) {
}
