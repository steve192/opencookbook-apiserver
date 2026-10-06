package com.sterul.opencookbookapiserver.services.retention;

import java.time.Clock;
import java.time.Duration;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.repositories.UserRepository;

/** When an account was last used, which restarts its way to deletion for disuse. */
@Service
@Transactional
public class AccountActivityService {

    /** The app renews its token every few minutes, and an api key may be polled every few seconds. */
    private static final Duration PRECISION = Duration.ofHours(1);

    private final UserRepository users;
    private final Clock clock;

    public AccountActivityService(UserRepository users, Clock clock) {
        this.users = users;
        this.clock = clock;
    }

    public void signedIn(CookpalUser user) {
        users.recordSignIn(user.getUserId(), clock.instant());
    }

    public void used(CookpalUser user) {
        var now = clock.instant();
        users.recordUse(user.getUserId(), now, now.minus(PRECISION));
    }
}
