package com.sterul.opencookbookapiserver.controllers.shopping.live;

import java.util.List;

/** What the server tells an app: never the data, only that there is something to pull. */
sealed interface ServerMessage {

    record Changed(String type, Long listId, long version) implements ServerMessage {
        Changed(Long listId, long version) {
            this("changed", listId, version);
        }
    }

    /** @param listIds the lists that will be hinted at; one the app may not read is left out */
    record Subscribed(String type, List<Long> listIds) implements ServerMessage {
        Subscribed(List<Long> listIds) {
            this("subscribed", listIds);
        }
    }
}
