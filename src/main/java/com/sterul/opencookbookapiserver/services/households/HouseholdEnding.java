package com.sterul.opencookbookapiserver.services.households;

/** A household is about to be deleted; published inside the deleting transaction. */
public record HouseholdEnding(String householdId) {
}
