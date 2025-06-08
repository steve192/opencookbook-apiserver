package com.sterul.opencookbookapiserver.integration;

import org.mockito.Mockito;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.repositories.UserRepository;

public final class TestUtils {

    public static CookpalUser getTestUser(UserRepository userRepository) {
        return userRepository.findByEmailAddress("test@test.com");
    }

    public static void whenAuthenticated(UserRepository userRepository) {
        // Mock currently logged in user
        Authentication authentication = Mockito.mock(Authentication.class);
        Mockito.when(authentication.getName()).thenReturn("test@test.com");

        SecurityContext securityContext = Mockito.mock(SecurityContext.class);
        Mockito.when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);

        var testUser = userRepository.findByEmailAddress("test@test.com");
        if (testUser == null) {
            testUser = new CookpalUser();
            testUser.setUserId(null);
            testUser.setEmailAddress("test@test.com");
            userRepository.save(testUser);
        }
    }

}
