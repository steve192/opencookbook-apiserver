package com.sterul.opencookbookapiserver.integration;

import com.sterul.opencookbookapiserver.controllers.UserController;
import com.sterul.opencookbookapiserver.controllers.exceptions.UnauthorizedException;
import com.sterul.opencookbookapiserver.controllers.requests.UserCreationRequest;
import com.sterul.opencookbookapiserver.controllers.requests.UserLoginRequest;
import com.sterul.opencookbookapiserver.entities.account.User;
import com.sterul.opencookbookapiserver.repositories.UserRepository;
import com.sterul.opencookbookapiserver.services.EmailService;
import com.sterul.opencookbookapiserver.services.exceptions.UserAlreadyExistsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import javax.mail.MessagingException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@SpringBootTest
class UserAPIIntegrationTest {

    final String testPassword = "12345";
    @MockBean
    UserRepository userRepository;
    @MockBean
    EmailService emailService;
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

    @Test
    @Transactional
    void registrationEmailSent() throws UserAlreadyExistsException, MessagingException {
        cut.signup(UserCreationRequest.builder().emailAddress("testi@cookpal.io").password("12345").build());
        verify(emailService, times(1)).sendActivationMail(any());
    }

    @Test
    @Transactional
    void nonActivatedUserCannotLogin() throws UserAlreadyExistsException, UnauthorizedException {
        var response = cut.login(UserLoginRequest.builder()
                .emailAddress(testUser.getEmailAddress())
                .password(testPassword)
                .build());

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertTrue(response.getBody().toString().contains("NOT_ACTIVE"));

    }

}
