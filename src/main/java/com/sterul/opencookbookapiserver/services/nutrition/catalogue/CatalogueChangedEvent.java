package com.sterul.opencookbookapiserver.services.nutrition.catalogue;

/** Listeners act once the publishing transaction, if any, has committed; {@code reason} is for the log. */
public record CatalogueChangedEvent(String reason) {
}
