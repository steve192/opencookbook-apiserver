package com.sterul.opencookbookapiserver.integration;

import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.repositories.UserRepository;

/** Activated accounts for tests that act as a signed-in user. */
final class TestAccounts {

    private TestAccounts() {
    }

    /** Creates the account unless it exists; accounts outlive a test class's cleanup. */
    static CookpalUser ensure(UserRepository users, String emailAddress) {
        var existing = users.findByEmailAddress(emailAddress);
        if (existing != null) {
            return existing;
        }
        var user = new CookpalUser();
        user.setEmailAddress(emailAddress);
        user.setPasswordHash("irrelevant");
        user.setActivated(true);
        user.setLanguage("de");
        return users.save(user);
    }
}
