package com.sterul.opencookbookapiserver.services.ml;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

import com.sterul.opencookbookapiserver.configurations.OpencookbookConfiguration;
import com.sterul.opencookbookapiserver.configurations.ml.ConditionalOnMlConfigured;

import lombok.extern.slf4j.Slf4j;

/** Whether the subsystem is usable right now. */
@Service
@ConditionalOnMlConfigured
@Slf4j
public class MlAvailabilityService {

    /** How long an answer is trusted. */
    private static final Duration FRESH_FOR = Duration.ofSeconds(60);

    private final MlSubsystemProxy proxy;
    private final OpencookbookConfiguration configuration;
    private final Clock clock;

    private volatile Instant checkedAt = Instant.MIN;
    private volatile boolean available = false;

    /**
     * Held by whoever is refreshing the answer. /api/v1/instance is public and asks this on
     * every call, so without it a burst of app launches would each spend a connect timeout on
     * the same unreachable subsystem.
     */
    private final Lock refreshing = new ReentrantLock();

    public MlAvailabilityService(MlSubsystemProxy proxy, OpencookbookConfiguration configuration,
            Clock clock) {
        this.proxy = proxy;
        this.configuration = configuration;
        this.clock = clock;
    }

    public boolean isRecipeOcrAvailable() {
        return configuration.getMl().getRecipeOcr().isEnabled() && isAvailable();
    }

    public boolean isAvailable() {
        if (!hasCredential()) {
            // The health endpoint needs no token, so asking it would say the subsystem is up
            // while every actual request is refused
            return false;
        }
        if (isFresh()) {
            return available;
        }
        if (!refreshing.tryLock()) {
            // Somebody is already asking. Their answer will be along shortly, and the previous
            // one is better than a queue of callers waiting on the same request.
            return available;
        }
        try {
            // The holder of the lock may have finished while this call was waiting for it.
            if (isFresh()) {
                return available;
            }
            proxy.health();
            record(true);
        } catch (MlSubsystemException e) {
            log.warn("The machine learning subsystem is not available: {}", e.getMessage());
            record(false);
        } finally {
            refreshing.unlock();
        }
        return available;
    }

    private boolean isFresh() {
        return Duration.between(checkedAt, clock.instant()).compareTo(FRESH_FOR) < 0;
    }

    private boolean hasCredential() {
        var apiToken = configuration.getMl().getApiToken();
        return apiToken != null && !apiToken.isBlank();
    }

    /** Warns once about the one configuration that looks finished but is not. */
    @PostConstruct
    void warnAboutAMissingToken() {
        if (!hasCredential()) {
            log.warn("A machine learning subsystem is configured at {} but no api token is set, "
                    + "so recipe scanning stays unavailable. Issue one with "
                    + "'manage.py issue_token' and set opencookbook.ml.apiToken.",
                    configuration.getMl().getServiceUrl());
        }
    }

    /** Remember what a real request just proved, so the next check does not have to ask again. */
    public void reportReachable() {
        record(true);
    }

    public void reportUnreachable() {
        record(false);
    }

    private void record(boolean reachable) {
        this.available = reachable;
        this.checkedAt = clock.instant();
    }
}
