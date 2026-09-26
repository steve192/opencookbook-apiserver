package com.sterul.opencookbookapiserver.services;

import org.springframework.stereotype.Service;

import com.sterul.opencookbookapiserver.entities.RefreshToken;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.errors.ApiErrorCode;
import com.sterul.opencookbookapiserver.errors.ApiException;
import com.sterul.opencookbookapiserver.services.exceptions.ElementNotFound;
import com.sterul.opencookbookapiserver.util.JwtTokenUtil;

/**
 * Hands out the tokens a client authenticates with.
 *
 * Signing in, activating an account and renewing all end in the same few steps, which used to
 * be written out at each of the three endpoints. Keeping them here means a controller never has
 * to know that an access token is made from Spring's UserDetails, or what a refresh token is
 * stored as.
 */
@Service
public class AuthTokenService {

    private final UserDetailsServiceImpl userDetailsService;
    private final JwtTokenUtil jwtTokenUtil;
    private final RefreshTokenService refreshTokenService;

    public AuthTokenService(UserDetailsServiceImpl userDetailsService, JwtTokenUtil jwtTokenUtil,
            RefreshTokenService refreshTokenService) {
        this.userDetailsService = userDetailsService;
        this.jwtTokenUtil = jwtTokenUtil;
        this.refreshTokenService = refreshTokenService;
    }

    /** Both tokens, for somebody who has just shown who they are. */
    public IssuedTokens issueFor(CookpalUser user) {
        return new IssuedTokens(accessTokenFor(user),
                refreshTokenService.createRefreshTokenForUser(user).getToken());
    }

    /** Only an access token: renewing does not hand out a new refresh token. */
    public String accessTokenFor(CookpalUser user) {
        return jwtTokenUtil.generateToken(userDetailsService.loadUserByUsername(user.getEmailAddress()));
    }

    /**
     * "Sign in again", not "you may not": a spent refresh token is the app's cue to send somebody
     * back to the login screen, and a refusal reads as a permission they will never have.
     */
    public RefreshToken requireValidRefreshToken(String refreshToken) {
        if (!refreshTokenService.isTokenValid(refreshToken)) {
            throw new ApiException(ApiErrorCode.AUTHENTICATION_REQUIRED, "Refresh token expired");
        }
        try {
            return refreshTokenService.getRefreshToken(refreshToken);
        } catch (ElementNotFound e) {
            throw new ApiException(ApiErrorCode.AUTHENTICATION_REQUIRED, "Refresh token unknown", e);
        }
    }

    /** What a client is given to authenticate with. */
    public record IssuedTokens(String accessToken, String refreshToken) {
    }
}
