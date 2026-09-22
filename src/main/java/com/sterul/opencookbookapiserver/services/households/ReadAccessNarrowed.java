package com.sterul.opencookbookapiserver.services.households;

import java.util.Set;

/**
 * These readers may read fewer cookbooks than before, so the household's plan and their own plans
 * may hold meals they can no longer open.
 */
public record ReadAccessNarrowed(String householdId, Set<Long> readerIds) {
}
