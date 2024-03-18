package com.sterul.opencookbookapiserver.integration;

import static com.sterul.opencookbookapiserver.integration.TestUtils.whenAuthenticated;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Calendar;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
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

        var future = Calendar.getInstance();
        future.add(Calendar.HOUR, 1);
        passwordResetLink = new PasswordResetLink();
        passwordResetLink.setUser(testUser);
        passwordResetLink.setId("test");
        passwordResetLink.setValidUntil(future.getTime());
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
