package com.sterul.opencookbookapiserver.controllers.catalogue;

import java.util.List;

/** Where the catalogue is read. The controllers and the security rules must agree on these. */
public final class CataloguePaths {

    public static final String BASE = "/api/v1/catalogue";

    public static final String SEARCH = BASE + "/search";

    public static final String SEASONS = BASE + "/seasons";

    public static final String SOAKING = BASE + "/soaking";

    /** What the kitchen tools read: public when the tools are. Read only. */
    public static final List<String> TOOL_PATHS = List.of(SEARCH, SEASONS, SOAKING);

    private CataloguePaths() {
    }
}
