package com.sterul.opencookbookapiserver.integration;

import com.sterul.opencookbookapiserver.controllers.UserController;
import com.sterul.opencookbookapiserver.controllers.exceptions.UnauthorizedException;
import com.sterul.opencookbookapiserver.controllers.requests.PasswordResetExecutionRequest;
import com.sterul.opencookbookapiserver.controllers.requests.PasswordResetRequest;
import com.sterul.opencookbookapiserver.controllers.requests.UserCreationRequest;
import com.sterul.opencookbookapiserver.controllers.requests.UserLoginRequest;
import com.sterul.opencookbookapiserver.entities.RefreshToken;
import com.sterul.opencookbookapiserver.entities.account.PasswordResetLink;
import com.sterul.opencookbookapiserver.entities.account.User;
import com.sterul.opencookbookapiserver.repositories.PasswordResetLinkRepository;
import com.sterul.opencookbookapiserver.repositories.UserRepository;
import com.sterul.opencookbookapiserver.services.EmailService;
import com.sterul.opencookbookapiserver.services.RefreshTokenService;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import javax.mail.MessagingException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
class UserAPIIntegrationTest {

    final String testPassword = "12345";
    @MockBean
    UserRepository userRepository;
    @MockBean
    EmailService emailService;

    @Autowired
    PasswordEncoder passwordEncoder;

    @MockBean
    RefreshTokenService refreshTokenService;

    @MockBean
    PasswordResetLinkRepository passwordResetLinkRepository;

    @Autowired
    UserController cut;

    User testUser;

    RefreshToken testRefreshToken;

    PasswordResetLink passwordResetLink;

    @BeforeEach
    void setup() {
        testUser = new User();
        testUser.setEmailAddress("test@test.com");
        testUser.setPasswordHash(passwordEncoder.encode(testPassword));

        testRefreshToken = new RefreshToken();
        testRefreshToken.setToken("test123");
        testRefreshToken.setOwner(testUser);
        when(refreshTokenService.createRefreshTokenForUser(testUser)).thenReturn(testRefreshToken);
        when(userRepository.findByEmailAddress(testUser.getEmailAddress())).thenReturn(testUser);
        when(userRepository.existsByEmailAddress(testUser.getEmailAddress())).thenReturn(true);

        passwordResetLink = new PasswordResetLink();
        passwordResetLink.setUser(testUser);
        passwordResetLink.setId("test");
        passwordResetLinkRepository.save(passwordResetLink);
    }

    void whenTestUserExists(boolean active) {
        testUser.setActivated(active);
        when(userRepository.findByEmailAddress(testUser.getEmailAddress())).thenReturn(testUser);
    }

    void whenAuthentificated() {
        // Mock currently logged in user
        Authentication authentication = Mockito.mock(Authentication.class);
        Mockito.when(authentication.getName()).thenReturn("test@test.com");

        SecurityContext securityContext = Mockito.mock(SecurityContext.class);
        Mockito.when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);
    }

    @Test
    void testUserInfo() {
        whenTestUserExists(true);
        whenAuthentificated();
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
    void nonActivatedUserCannotLogin() throws UnauthorizedException {
        whenTestUserExists(false);
        var response = cut.login(UserLoginRequest.builder()
                .emailAddress(testUser.getEmailAddress())
                .password(testPassword)
                .build());

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertFalse(response.getBody().isUserActive());
    }

    @Test
    void passwordRequestForNonExtantUserIsSuccessful() {
        var response = cut.requestPasswordReset(
                PasswordResetRequest.builder()
                        .emailAddress("nonexistant@example.com")
                        .build());
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void errorWhenPasswordResetLinkDoesNotExists() {
        var response = cut.resetPassword(PasswordResetExecutionRequest.builder()
                .passwordResetId("not existant")
                .newPassword("does not matter")
                .build());
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void passwordIsReset() {
        cut.resetPassword(PasswordResetExecutionRequest.builder()
                .passwordResetId(passwordResetLink.getId())
                .newPassword("12345")
                .build());

        testUser.setPasswordHash(passwordEncoder.encode("12345"));
        verify(userRepository, times(1)).save(testUser);
    }

    @Test
    void passwordResetRequestSendsMail() throws MessagingException {
        whenTestUserExists(true);
        cut.requestPasswordReset(PasswordResetRequest.builder()
                .emailAddress(testUser.getEmailAddress())
                .build());

        verify(emailService, times(1)).sendPasswordResetMail(any());
    }

    @Test
    void activeUserCanLogin() throws UnauthorizedException {
        whenTestUserExists(true);

        var response = cut.login(UserLoginRequest.builder()
                .emailAddress(testUser.getEmailAddress())
                .password(testPassword)
                .build());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().toString().contains(testRefreshToken.getToken()));
    }

}
