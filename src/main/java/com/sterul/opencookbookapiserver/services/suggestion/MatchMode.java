package com.sterul.opencookbookapiserver.services.suggestion;

/** How the ingredients a cook named are meant. */
public enum MatchMode {
    /** Any of them qualifies a recipe; using more of them ranks it higher. */
    ANY_RANKED,
    /** A recipe qualifies only when it contains all of them. */
    MUST_CONTAIN
}
