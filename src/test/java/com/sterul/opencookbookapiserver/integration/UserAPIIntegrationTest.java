package com.sterul.opencookbookapiserver.integration;

import static com.sterul.opencookbookapiserver.integration.TestUtils.whenAuthenticated;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import com.sterul.opencookbookapiserver.controllers.UserController;
import com.sterul.opencookbookapiserver.controllers.exceptions.UnauthorizedException;
import com.sterul.opencookbookapiserver.controllers.requests.PasswordChangeRequest;
import com.sterul.opencookbookapiserver.controllers.requests.PasswordResetExecutionRequest;
import com.sterul.opencookbookapiserver.controllers.requests.PasswordResetRequest;
import com.sterul.opencookbookapiserver.controllers.requests.UserCreationRequest;
import com.sterul.opencookbookapiserver.controllers.requests.UserLoginRequest;
import com.sterul.opencookbookapiserver.entities.RefreshToken;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.entities.account.PasswordResetLink;
import com.sterul.opencookbookapiserver.repositories.ActivationLinkRepository;
import com.sterul.opencookbookapiserver.repositories.PasswordResetLinkRepository;
import com.sterul.opencookbookapiserver.repositories.UserRepository;
import com.sterul.opencookbookapiserver.services.EmailService;
import com.sterul.opencookbookapiserver.services.RefreshTokenService;
import com.sterul.opencookbookapiserver.services.exceptions.SignupDisabledException;
import com.sterul.opencookbookapiserver.services.exceptions.UserAlreadyExistsException;

import jakarta.mail.MessagingException;

@SpringBootTest
@ActiveProfiles("integration-test")
class UserAPIIntegrationTest extends IntegrationTest{

    final String testPassword = "12345";

    @MockitoBean
    UserRepository userRepository;

    @MockitoBean
    EmailService emailService;

    @Autowired
    PasswordEncoder passwordEncoder;

    @MockitoBean
    RefreshTokenService refreshTokenService;

    @MockitoBean
    PasswordResetLinkRepository passwordResetLinkRepository;

    @MockitoBean
    ActivationLinkRepository activationLinkRepository;

    @Autowired
    UserController cut;

    CookpalUser testUser;

    RefreshToken testRefreshToken;

    PasswordResetLink passwordResetLink;

    @BeforeEach
    void setup() {
        testUser = new CookpalUser();
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
        passwordResetLink.setValidUntil(Instant.now().plus(1, ChronoUnit.HOURS));
        when(passwordResetLinkRepository.findById(passwordResetLink.getId()))
                .thenReturn(Optional.of(passwordResetLink));
    }

    @Test
    void passwordChangeWithCorrectPasswordIsSuccessfull() {
        whenAuthenticated(userRepository);

        var response = cut.changePassword(PasswordChangeRequest.builder()
                .oldPassword(testPassword)
                .newPassword("blablabla")
                .build());

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void passwordChangeWithWrongPasswordFails() {
        whenAuthenticated(userRepository);

        var response = cut.changePassword(PasswordChangeRequest.builder()
                .oldPassword(testPassword + "wrong")
                .newPassword("blablabla")
                .build());

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    void whenTestUserExists(boolean active) {
        testUser.setActivated(active);
        when(userRepository.findByEmailAddress(testUser.getEmailAddress())).thenReturn(testUser);
    }


    @Test
    void testUserInfo() {
        whenAuthenticated(userRepository);
        assertEquals(cut.getOwnUserInfo().getEmail(), testUser.getEmailAddress());
    }

    @Test
    @Transactional
    void registrationEmailSent() throws UserAlreadyExistsException, MessagingException, SignupDisabledException {
        cut.signup(new UserCreationRequest("testi@cookpal.io", "12345"));
        verify(emailService, times(1)).sendActivationMail(any());
    }

    @Test
    @Transactional
    void nonActivatedUserCannotLogin() throws UnauthorizedException, MessagingException {
        whenTestUserExists(false);

        var response = cut.login(new UserLoginRequest(testUser.getEmailAddress(), testPassword));

        verify(activationLinkRepository, times(1)).deleteAllByUser(any());
        verify(activationLinkRepository, times(1)).save(any());

        

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
        final var newPassword = "12345";
        final var newPasswordHash = passwordEncoder.encode(newPassword);

        cut.resetPassword(PasswordResetExecutionRequest.builder()
                .passwordResetId(passwordResetLink.getId())
                .newPassword(newPassword)
                .build());

        testUser.setPasswordHash(passwordEncoder.encode(newPassword));
        verify(userRepository, times(1)).save(any());
    }

    @Test
    void passwordResetRequestSendsMail() throws MessagingException {
        whenTestUserExists(true);
        cut.requestPasswordReset(PasswordResetRequest.builder()
                .emailAddress(testUser.getEmailAddress())
                .build());

        verify(emailService, times(1)).sendPasswordResetMail(any());
    }

    // A dropped ResponseEntity used to leave this at 200, so the app told people to check an
    // inbox that never received anything.
    @Test
    void passwordResetRequestReportsWhenTheMailCannotBeSent() throws MessagingException {
        whenTestUserExists(true);
        doThrow(new MessagingException("mail server down")).when(emailService).sendPasswordResetMail(any());

        var response = cut.requestPasswordReset(PasswordResetRequest.builder()
                .emailAddress(testUser.getEmailAddress())
                .build());

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    }

    @Test
    void activeUserCanLogin() throws UnauthorizedException, MessagingException {
        whenTestUserExists(true);

        var response = cut.login(new UserLoginRequest(testUser.getEmailAddress(), testPassword));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().toString().contains(testRefreshToken.getToken()));
    }

}
