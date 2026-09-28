package com.sterul.opencookbookapiserver.controllers.shopping.live;

import java.util.List;

/** What an app sends: {@code subscribe} with the lists it shows, again whenever that changes. */
record ClientMessage(String type, List<ListReference> lists) {

    static final String SUBSCRIBE = "subscribe";

    /** @param householdId null for a person's own list */
    record ListReference(Long listId, String householdId) {
    }
}
