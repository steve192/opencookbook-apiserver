package com.sterul.opencookbookapiserver.services;

import java.time.Clock;
import java.time.Instant;

import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import com.sterul.opencookbookapiserver.configurations.OpencookbookConfiguration;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;

/**
 * Short-lived access tokens (JWT). They name the account by id and carry no roles: those are read anew
 * on every request, so a changed role or a deactivated account takes effect at once.
 */
@Service
public class AccessTokenService {

    /** The sign in a token was issued for, named as in OpenID Connect. */
    public static final String SESSION_CLAIM = "sid";

    private final JwtEncoder encoder;
    private final OpencookbookConfiguration configuration;
    private final Clock clock;

    public AccessTokenService(JwtEncoder encoder, OpencookbookConfiguration configuration, Clock clock) {
        this.encoder = encoder;
        this.configuration = configuration;
        this.clock = clock;
    }

    public String issue(CookpalUser user, String sessionId, Instant passwordAt) {
        var now = clock.instant();
        var claims = JwtClaimsSet.builder()
                .subject(String.valueOf(user.getUserId()))
                .issuedAt(now)
                .expiresAt(now.plus(configuration.getJwtDuration()))
                .claim(SESSION_CLAIM, sessionId);
        if (passwordAt != null) {
            claims.claim(IdTokenClaimNames.AUTH_TIME, passwordAt.getEpochSecond());
        }
        return encoder.encode(JwtEncoderParameters.from(claims.build())).getTokenValue();
    }
}
