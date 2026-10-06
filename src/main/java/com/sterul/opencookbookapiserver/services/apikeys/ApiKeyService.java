package com.sterul.opencookbookapiserver.services.apikeys;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sterul.opencookbookapiserver.configurations.OpencookbookConfiguration;
import com.sterul.opencookbookapiserver.entities.account.ApiKey;
import com.sterul.opencookbookapiserver.entities.account.ApiScope;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.errors.ApiErrorCode;
import com.sterul.opencookbookapiserver.errors.ApiException;
import com.sterul.opencookbookapiserver.repositories.ApiKeyRepository;
import com.sterul.opencookbookapiserver.services.SecretTokens;
import com.sterul.opencookbookapiserver.services.retention.AccountActivityService;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class ApiKeyService {

    /** Tells a key from a JWT at a glance, and makes a leaked one findable by secret scanners. */
    public static final String PREFIX = "cpk_";

    private static final int DISPLAY_PREFIX_LENGTH = PREFIX.length() + 6;
    /** A key polled every few seconds writes its last use this often at most. */
    private static final Duration LAST_USED_PRECISION = Duration.ofMinutes(1);

    private final ApiKeyRepository keys;
    private final AccountActivityService activity;
    private final OpencookbookConfiguration configuration;
    private final Clock clock;

    public ApiKeyService(ApiKeyRepository keys, AccountActivityService activity, OpencookbookConfiguration configuration,
            Clock clock) {
        this.keys = keys;
        this.activity = activity;
        this.configuration = configuration;
        this.clock = clock;
    }

    public static boolean looksLikeKey(String token) {
        return token.startsWith(PREFIX);
    }

    public record IssuedKey(ApiKey key, String secret) {
    }

    @Transactional
    public IssuedKey create(CookpalUser owner, String name, Set<ApiScope> scopes) {
        if (keys.countByOwner(owner) >= configuration.getApiKeys().getMaxPerUser()) {
            throw new ApiException(ApiErrorCode.TOO_MANY_API_KEYS);
        }
        var secret = PREFIX + SecretTokens.generate();
        var key = keys.save(ApiKey.builder()
                .owner(owner)
                .name(name.strip())
                .secretHash(SecretTokens.hash(secret))
                .displayPrefix(secret.substring(0, DISPLAY_PREFIX_LENGTH))
                .scopes(ApiScope.withImplied(scopes))
                .build());
        log.info("Created api key {} for user {}", key.getId(), owner);
        return new IssuedKey(key, secret);
    }

    public List<ApiKey> keysOf(CookpalUser owner) {
        return keys.findAllByOwnerOrderByCreatedOnDesc(owner);
    }

    @Transactional
    public void revoke(CookpalUser owner, Long id) {
        var key = keys.findByIdAndOwner(id, owner).orElseThrow(() -> new ApiException(ApiErrorCode.RESOURCE_NOT_FOUND));
        keys.delete(key);
        log.info("Revoked api key {} of user {}", id, owner);
    }

    @Transactional
    public Optional<ApiKey> authenticate(String secret) {
        var key = keys.findBySecretHash(SecretTokens.hash(secret)).filter(found -> found.getOwner().isActivated());
        key.ifPresent(this::recordUse);
        return key;
    }

    private void recordUse(ApiKey key) {
        var now = clock.instant();
        var staleBefore = now.minus(LAST_USED_PRECISION);
        if (key.getLastUsedAt() == null || key.getLastUsedAt().isBefore(staleBefore)) {
            keys.touch(key.getId(), now, staleBefore);
            activity.used(key.getOwner());
        }
    }
}
