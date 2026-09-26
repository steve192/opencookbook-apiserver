package com.sterul.opencookbookapiserver.integration;

import static com.sterul.opencookbookapiserver.integration.TestUtils.whenAuthenticated;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import com.sterul.opencookbookapiserver.errors.ApiErrorCode;
import com.sterul.opencookbookapiserver.controllers.exceptions.UnauthorizedException;
import com.sterul.opencookbookapiserver.controllers.exceptions.UserNotActiveException;
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
import com.sterul.opencookbookapiserver.services.exceptions.PasswordResetLinkNotExistingException;

import jakarta.mail.MessagingException;

@SpringBootTest
@ActiveProfiles("integration-test")
class UserAPIIntegrationTest extends IntegrationTestBase{

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

        var request = PasswordChangeRequest.builder()
                .oldPassword(testPassword + "wrong")
                .newPassword("blablabla")
                .build();

        var thrown = assertThrows(UnauthorizedException.class, () -> cut.changePassword(request));

        assertEquals(ApiErrorCode.INVALID_CREDENTIALS, thrown.getErrorCode());
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
    void registrationEmailSent() throws MessagingException {
        cut.signup(new UserCreationRequest("testi@cookpal.io", "12345"));
        verify(emailService, times(1)).sendActivationMail(any());
    }

    @Test
    @Transactional
    void nonActivatedUserCannotLogin() {
        whenTestUserExists(false);

        var request = new UserLoginRequest(testUser.getEmailAddress(), testPassword);

        var thrown = assertThrows(UserNotActiveException.class, () -> cut.login(request));

        // The link is sent again on the way out, because somebody trying to sign in is somebody
        // who never received it or lost it.
        verify(activationLinkRepository, times(1)).deleteAllByUser(any());
        verify(activationLinkRepository, times(1)).save(any());

        assertEquals(ApiErrorCode.ACCOUNT_NOT_ACTIVATED, thrown.getErrorCode());
    }

    @Test
    void passwordRequestForNonExtantUserIsSuccessful() throws MessagingException {
        var response = cut.requestPasswordReset(
                PasswordResetRequest.builder()
                        .emailAddress("nonexistant@example.com")
                        .build());
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void errorWhenPasswordResetLinkDoesNotExists() {
        var request = PasswordResetExecutionRequest.builder()
                .passwordResetId("not existant")
                .newPassword("does not matter")
                .build();

        var thrown = assertThrows(PasswordResetLinkNotExistingException.class,
                () -> cut.resetPassword(request));

        assertEquals(ApiErrorCode.PASSWORD_RESET_LINK_INVALID, thrown.getErrorCode());
    }

    @Test
    void passwordIsReset() {
        final var newPassword = "12345";

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
    // inbox that never received anything. It now travels to the handler, which answers
    // MAIL_DELIVERY_FAILED.
    @Test
    void passwordResetRequestReportsWhenTheMailCannotBeSent() throws MessagingException {
        whenTestUserExists(true);
        doThrow(new MessagingException("mail server down")).when(emailService).sendPasswordResetMail(any());

        assertThrows(MessagingException.class,
                () -> cut.requestPasswordReset(PasswordResetRequest.builder()
                        .emailAddress(testUser.getEmailAddress())
                        .build()));
    }

    @Test
    void activeUserCanLogin() {
        whenTestUserExists(true);

        var response = cut.login(new UserLoginRequest(testUser.getEmailAddress(), testPassword));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().toString().contains(testRefreshToken.getToken()));
    }

}
