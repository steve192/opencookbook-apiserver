package com.sterul.opencookbookapiserver.controllers.support;

import java.io.IOException;
import java.time.Duration;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import com.sterul.opencookbookapiserver.services.exceptions.ElementNotFound;

import lombok.extern.slf4j.Slf4j;

/**
 * Serving a stored image over HTTP.
 */
@Slf4j
public final class RecipeImageResponses {

    private static final Duration CACHE_DURATION = Duration.ofDays(1);

    private RecipeImageResponses() {
    }

    /** Serves an image only the requesting user may see. */
    public static ResponseEntity<byte[]> servePrivately(ImageSource source, String imageUuid)
            throws ElementNotFound {
        return serve(source, imageUuid, cacheFor(CacheControl.maxAge(CACHE_DURATION).cachePrivate()));
    }

    /**
     * Serves an image that anybody holding a share link may see. Cacheable by intermediaries,
     * unlike the owner's own images: the link is meant to be handed around.
     */
    public static ResponseEntity<byte[]> servePublicly(ImageSource source, String imageUuid)
            throws ElementNotFound {
        return serve(source, imageUuid, cacheFor(CacheControl.maxAge(CACHE_DURATION).cachePublic()));
    }

    private static ResponseEntity<byte[]> serve(ImageSource source, String imageUuid, CacheControl cacheControl)
            throws ElementNotFound {
        byte[] imageData;
        try {
            imageData = source.read();
        } catch (IOException e) {
            log.warn("Exception while loading image {}", imageUuid, e);
            throw new ElementNotFound();
        }

        return ResponseEntity.ok()
                .cacheControl(cacheControl)
                .contentType(MediaType.IMAGE_JPEG)
                .body(imageData);
    }

    private static CacheControl cacheFor(CacheControl cacheControl) {
        // Proxies must not recompress a jpeg on the way through; the app compares bytes it has
        // cached itself against what it is served.
        return cacheControl.noTransform();
    }

    @FunctionalInterface
    public interface ImageSource {
        byte[] read() throws IOException;
    }
}
