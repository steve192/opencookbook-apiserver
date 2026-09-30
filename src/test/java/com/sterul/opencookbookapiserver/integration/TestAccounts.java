package com.sterul.opencookbookapiserver.integration;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import org.springframework.http.HttpHeaders;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.sterul.opencookbookapiserver.configurations.security.AccountAuthorities;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.entities.account.Role;
import com.sterul.opencookbookapiserver.repositories.UserRepository;

/** Activated accounts for tests that act as a signed-in user. */
final class TestAccounts {

    private TestAccounts() {
    }

    /** Creates the account unless it exists; accounts outlive a test class's cleanup. */
    static CookpalUser ensure(UserRepository users, String emailAddress) {
        var existing = users.findByEmailAddress(emailAddress);
        return existing != null ? existing : users.save(activated(emailAddress, "irrelevant"));
    }

    /** A fresh account for signing in for real; whatever an earlier test left of it goes first. */
    static CookpalUser recreate(UserRepository users, String emailAddress, String passwordHash) {
        var existing = users.findByEmailAddress(emailAddress);
        if (existing != null) {
            users.delete(existing);
        }
        return users.save(activated(emailAddress, passwordHash));
    }

    /** An administrator who has just entered the password, as the admin api asks. */
    static RequestPostProcessor operator(String emailAddress) {
        return user(emailAddress).authorities(AccountAuthorities.role(Role.ADMIN),
                FactorGrantedAuthority.fromAuthority(FactorGrantedAuthority.PASSWORD_AUTHORITY));
    }

    /** An access token or api key, sent as a client sends it. */
    static RequestPostProcessor bearer(String token) {
        return request -> {
            request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
            return request;
        };
    }

    private static CookpalUser activated(String emailAddress, String passwordHash) {
        var user = new CookpalUser();
        user.setEmailAddress(emailAddress);
        user.setPasswordHash(passwordHash);
        user.setActivated(true);
        user.setLanguage("de");
        return user;
    }
}
