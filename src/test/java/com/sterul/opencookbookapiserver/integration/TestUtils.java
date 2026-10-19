package com.sterul.opencookbookapiserver.integration;

import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;

import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.repositories.UserRepository;

public final class TestUtils {

    public static CookpalUser getTestUser(UserRepository userRepository) {
        return userRepository.findByEmailAddress("test@test.com");
    }

    public static void whenAuthenticated(UserRepository userRepository) {
        SecurityContextHolder.setContext(new SecurityContextImpl(
                UsernamePasswordAuthenticationToken.authenticated("test@test.com", null, List.of())));

        var testUser = userRepository.findByEmailAddress("test@test.com");
        if (testUser == null) {
            testUser = new CookpalUser();
            testUser.setUserId(null);
            testUser.setEmailAddress("test@test.com");
            userRepository.save(testUser);
        }
    }

}
