package com.sterul.opencookbookapiserver.integration;

import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.entities.account.Role;
import com.sterul.opencookbookapiserver.repositories.UserRepository;

/** An instance whose setup is done, which signing up asks for. */
final class TestInstance {

    static final String ADMINISTRATOR = "instance-admin@cookpal.invalid";

    private TestInstance() {
    }

    /** @return its activated administrator */
    static CookpalUser setUp(UserRepository users) {
        var administrator = TestAccounts.ensure(users, ADMINISTRATOR);
        administrator.setRoles(Role.ADMIN);
        administrator.setActivated(true);
        return users.save(administrator);
    }
}
