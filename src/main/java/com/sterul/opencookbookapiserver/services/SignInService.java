package com.sterul.opencookbookapiserver.services;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sterul.opencookbookapiserver.configurations.OpencookbookConfiguration;
import com.sterul.opencookbookapiserver.entities.RefreshToken;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.errors.ApiErrorCode;
import com.sterul.opencookbookapiserver.errors.ApiException;
import com.sterul.opencookbookapiserver.repositories.RefreshTokenRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * Sign ins, and the tokens a client authenticates with: a short-lived access token, and a refresh token
 * that renews it. A renewal may replace the refresh token; if a replaced one turns up again, somebody
 * holds a copy, and the whole sign in ends for them and its owner alike.
 */
@Service
// A refusal must not roll back what ended the sign in.
@Transactional(noRollbackFor = ApiException.class)
@Slf4j
public class SignInService {

    /** Two renewals at once, as from two browser tabs, present the same token; neither is a stolen copy. */
    static final Duration CONCURRENT_RENEWAL = Duration.ofSeconds(30);

    /** A refresh token starts with its sign in, so it is still recognised once its own row is gone. */
    private static final char SESSION_ID_END = '.';

    private final RefreshTokenRepository tokens;
    private final AccessTokenService accessTokens;
    private final OpencookbookConfiguration configuration;
    private final Clock clock;

    public SignInService(RefreshTokenRepository tokens, AccessTokenService accessTokens,
            OpencookbookConfiguration configuration, Clock clock) {
        this.tokens = tokens;
        this.accessTokens = accessTokens;
        this.configuration = configuration;
        this.clock = clock;
    }

    public record IssuedTokens(String accessToken, String refreshToken, CookpalUser user) {
    }

    public IssuedTokens signInWithPassword(CookpalUser user) {
        return issue(user, UUID.randomUUID().toString(), clock.instant());
    }

    /** By activation link or Google; the admin api will still ask for the password. */
    public IssuedTokens signInWithoutPassword(CookpalUser user) {
        return issue(user, UUID.randomUUID().toString(), null);
    }

    /** Renews with a new refresh token, which the client keeps instead of the one it sent. */
    public IssuedTokens rotate(String refreshToken) {
        var token = requireUsable(refreshToken);
        if (token.getReplacedAt() == null) {
            token.setReplacedAt(clock.instant());
        }
        return issue(token.getOwner(), token.getSessionId(), token.getPasswordAt());
    }

    /** Signing out: the sign in the token belongs to ends. Unknown tokens are ignored. */
    public void end(String refreshToken) {
        tokens.findByTokenHash(SecretTokens.hash(refreshToken))
                .map(RefreshToken::getSessionId)
                .or(() -> sessionIdIn(refreshToken))
                .ifPresent(tokens::deleteSession);
    }

    public void endAll(CookpalUser owner) {
        tokens.deleteAllOf(owner);
    }

    /** Also counts the kept sign in as made with the password from now on. */
    public void keepOnly(CookpalUser owner, String sessionId) {
        tokens.deleteAllOfBut(owner, sessionId);
        tokens.recordPassword(sessionId, clock.instant());
    }

    /** Expired tokens, and replaced ones once no concurrent renewal can present them any more. */
    public int deleteStale() {
        var now = clock.instant();
        return tokens.deleteStale(now, now.minus(CONCURRENT_RENEWAL));
    }

    private RefreshToken requireUsable(String refreshToken) {
        var found = tokens.findByTokenHash(SecretTokens.hash(refreshToken));
        if (found.isEmpty()) {
            sessionIdIn(refreshToken).flatMap(tokens::findFirstBySessionId).ifPresent(this::endReplayed);
            throw signInAgain();
        }
        var token = found.get();
        var now = clock.instant();
        if (token.getValidUntil().isBefore(now)) {
            tokens.delete(token);
            throw signInAgain();
        }
        if (!token.getOwner().isActivated()) {
            endAll(token.getOwner());
            throw signInAgain();
        }
        if (token.getReplacedAt() != null && token.getReplacedAt().plus(CONCURRENT_RENEWAL).isBefore(now)) {
            endReplayed(token);
            throw signInAgain();
        }
        return token;
    }

    private void endReplayed(RefreshToken tokenOfTheSignIn) {
        log.warn("A replaced refresh token of user {} was used again; ending that sign in",
                tokenOfTheSignIn.getOwner());
        tokens.deleteSession(tokenOfTheSignIn.getSessionId());
    }

    private IssuedTokens issue(CookpalUser owner, String sessionId, Instant passwordAt) {
        var refreshToken = sessionId + SESSION_ID_END + SecretTokens.generate();
        tokens.save(RefreshToken.builder()
                .owner(owner)
                .tokenHash(SecretTokens.hash(refreshToken))
                .sessionId(sessionId)
                .passwordAt(passwordAt)
                .validUntil(clock.instant().plus(configuration.getRefreshTokenDuration()))
                .build());
        return new IssuedTokens(accessTokens.issue(owner, sessionId, passwordAt), refreshToken, owner);
    }

    /** Empty for tokens handed out before they named their sign in. */
    private static Optional<String> sessionIdIn(String refreshToken) {
        var end = refreshToken.indexOf(SESSION_ID_END);
        return end > 0 ? Optional.of(refreshToken.substring(0, end)) : Optional.empty();
    }

    /**
     * "Sign in again", not "you may not": a spent refresh token is the app's cue to send somebody back to
     * the login screen, and a refusal would read as a permission they will never have.
     */
    private static ApiException signInAgain() {
        return new ApiException(ApiErrorCode.AUTHENTICATION_REQUIRED, "Refresh token unknown, expired or replaced");
    }
}
