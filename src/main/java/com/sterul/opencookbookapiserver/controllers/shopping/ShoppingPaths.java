package com.sterul.opencookbookapiserver.controllers.shopping;

/** Shared by the controllers and the live channel. */
public final class ShoppingPaths {

    public static final String BASE = "/api/v1/shopping";

    /** A WebSocket; see ShoppingLiveHandler. */
    public static final String LIVE = BASE + "/live";

    public static final String VOCABULARY = BASE + "/vocabulary";

    private ShoppingPaths() {
    }
}
