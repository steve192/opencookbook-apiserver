package com.sterul.opencookbookapiserver.controllers.catalogue;

import java.time.Duration;

import org.springframework.http.CacheControl;

/** What the endpoints reading catalogue data share. */
public final class CatalogueResponses {

    /**
     * Dataset facts change only with a release. Public, so an answer must depend on its address alone: a language
     * comes as a parameter, never from the account.
     */
    public static final CacheControl RELEASE_CACHE = CacheControl.maxAge(Duration.ofDays(1)).cachePublic();

    /** An ISO 639-1 language code. */
    public static final String LANGUAGE = "[a-z]{2}";

    private CatalogueResponses() {
    }
}
