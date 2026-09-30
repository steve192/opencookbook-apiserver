package com.sterul.opencookbookapiserver.configurations.security;

import java.util.List;
import java.util.stream.Stream;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.entities.account.Role;

/** The authorities an account holds by its role, however it signed in. */
public final class AccountAuthorities {

    private AccountAuthorities() {
    }

    public static List<GrantedAuthority> of(CookpalUser user) {
        return Stream.ofNullable(user.getRoles()).map(AccountAuthorities::role).toList();
    }

    public static GrantedAuthority role(Role role) {
        return new SimpleGrantedAuthority("ROLE_" + role.name());
    }
}
