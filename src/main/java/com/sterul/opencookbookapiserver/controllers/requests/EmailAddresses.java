package com.sterul.opencookbookapiserver.controllers.requests;

public final class EmailAddresses {

    /** What RFC 5321 allows, and what the column holds. Longer would fail on insert instead. */
    public static final int MAX_LENGTH = 254;

    private EmailAddresses() {
    }
}
