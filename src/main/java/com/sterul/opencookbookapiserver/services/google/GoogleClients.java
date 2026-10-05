package com.sterul.opencookbookapiserver.services.google;

import java.util.Optional;

import com.sterul.opencookbookapiserver.configurations.OpencookbookConfiguration;

/**
 * The OAuth clients the app signs in to Google with. Public by design: they travel to Google in the clear.
 *
 * @param androidClientId null when only the web app offers Google
 */
public record GoogleClients(String clientId, String androidClientId) {

    /** Empty when the instance does not offer signing in with Google. */
    public static Optional<GoogleClients> of(OpencookbookConfiguration.Auth.Google google) {
        if (!google.isEnabled()) {
            return Optional.empty();
        }
        var android = google.getAndroidClientId();
        return Optional.of(new GoogleClients(google.getClientId(), android.isBlank() ? null : android));
    }
}
