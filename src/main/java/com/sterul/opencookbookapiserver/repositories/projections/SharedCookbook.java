package com.sterul.opencookbookapiserver.repositories.projections;

/** A member's cookbook as shown in one household. */
public record SharedCookbook(String householdId, Long ownerId) {
}
