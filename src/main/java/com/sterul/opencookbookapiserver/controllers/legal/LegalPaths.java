package com.sterul.opencookbookapiserver.controllers.legal;

/** Where the legal documents are served. They are public: people read them before they sign up. */
public final class LegalPaths {

    public static final String BASE = "/api/v1/legal";

    /** What the security whitelist opens up. */
    public static final String PUBLIC_PATTERN = BASE + "/**";

    private LegalPaths() {
    }
}
