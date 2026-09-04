package com.sterul.opencookbookapiserver.services.ml;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

import com.sterul.opencookbookapiserver.configurations.OpencookbookConfiguration;
import com.sterul.opencookbookapiserver.configurations.ml.ConditionalOnMlConfigured;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;

/** Turns a user into an opaque id the subsystem can group by but not identify. */
@Component
@ConditionalOnMlConfigured
public class MlSubmitterIds {

    private static final String ALGORITHM = "HmacSHA256";

    /**
     * Kept distinct from every other use of the same key, so that an id derived here can never
     * collide with, or be mistaken for, something derived elsewhere.
     */
    private static final String PURPOSE = "ml-submitter:";

    private final OpencookbookConfiguration configuration;

    public MlSubmitterIds(OpencookbookConfiguration configuration) {
        this.configuration = configuration;
    }

    public String of(CookpalUser user) throws MlUnavailableException {
        var salt = configuration.getMl().effectiveSubmitterSalt();
        if (salt.isBlank()) {
            // No token and no salt: there is nothing to key the derivation with. That is the
            // same "configured but not usable" state an unreachable subsystem is in, and it
            // has to read that way rather than as an internal error.
            throw new MlUnavailableException("ML_NO_CREDENTIAL",
                    "This instance has no machine learning credential configured");
        }
        try {
            var mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(salt.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            var digest = mac.doFinal(
                    (PURPOSE + user.getUserId()).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            // Both mean the jvm cannot do HmacSHA256, which is not a runtime condition worth
            // a checked exception on every caller.
            throw new IllegalStateException("Cannot derive a submitter id", e);
        }
    }
}
