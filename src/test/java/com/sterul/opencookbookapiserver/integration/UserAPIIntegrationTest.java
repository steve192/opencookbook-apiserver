package com.sterul.opencookbookapiserver.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.sterul.opencookbookapiserver.controllers.UserController;
import com.sterul.opencookbookapiserver.entities.account.User;
import com.sterul.opencookbookapiserver.repositories.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

@SpringBootTest
class UserAPIIntegrationTest {

    @MockBean
    UserRepository userRepository;

    @Autowired
    UserController cut;

    User testUser;

    @BeforeEach
    void setup() {
        testUser = new User();
        testUser.setEmailAddress("test@test.com");
        when(userRepository.findByEmailAddress(testUser.getEmailAddress())).thenReturn(testUser);

        // Mock currently logged in user
        Authentication authentication = Mockito.mock(Authentication.class);
        Mockito.when(authentication.getName()).thenReturn("test@test.com");

        SecurityContext securityContext = Mockito.mock(SecurityContext.class);
        Mockito.when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);
    }

    @Test
    void testUserInfo() {
        assertEquals(cut.getOwnUserInfo().getEmail(), testUser.getEmailAddress());
    }

}
