package com.sterul.opencookbookapiserver.services.access;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Whose recipes a viewer may read, and which of the viewer's households show each of those cookbooks.
 *
 * @param shownIn owner id to the ids of the viewer's households showing that owner's cookbook
 */
public record ReadableCookbooks(Long viewerId, Map<Long, Set<String>> shownIn) {

    /** Always contains the viewer. */
    public Set<Long> ownerIds() {
        var owners = new HashSet<>(shownIn.keySet());
        owners.add(viewerId);
        return owners;
    }

    public Set<String> householdsShowing(Long ownerId) {
        return shownIn.getOrDefault(ownerId, Set.of());
    }
}
