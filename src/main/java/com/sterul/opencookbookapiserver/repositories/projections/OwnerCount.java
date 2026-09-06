package com.sterul.opencookbookapiserver.repositories.projections;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** How much of something one person owns: one grouped query, not a count per person. */
public interface OwnerCount {

    Long getUserId();

    long getCount();

    /**
     * By user id. People who own nothing are absent. The queries producing these group by user
     * id, so no id appears twice and toMap may refuse a duplicate rather than pick a winner.
     */
    static Map<Long, Long> asMap(List<? extends OwnerCount> counts) {
        return counts.stream().collect(Collectors.toMap(OwnerCount::getUserId, OwnerCount::getCount));
    }
}
