package com.sterul.opencookbookapiserver.integration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import com.sterul.opencookbookapiserver.services.SignupState;
import com.sterul.opencookbookapiserver.services.UserService;

/** An SMTP host: open signups are confirmed through a mailed link, as before. */
@TestPropertySource(properties = {
        "opencookbook.smtp-host=smtp.cookpal.invalid",
        "opencookbook.instanceURL=https://cookpal.invalid",
        "opencookbook.mail-from=cookpal@cookpal.invalid"
})
class SignupWithMailIntegrationTest extends SignupMatrixIntegrationTest {

    @Autowired
    private UserService userService;

    @Override
    SignupState openSignupState() {
        return SignupState.AWAITING_CONFIRMATION;
    }

    @Test
    void anOpenSignupIsMailedItsActivationLink() throws Exception {
        signUp(newAddress(), null).andExpect(status().isOk());

        verify(emailService, times(1)).sendActivationMail(any());
    }

    @Test
    void anInvitedSignupNeedsNoMail() throws Exception {
        signUp(newAddress(), invitation(Duration.ofDays(1)).getId())
                .andExpect(jsonPath("$.state").value("ACTIVE"));

        verify(emailService, never()).sendActivationMail(any());
    }

    @Test
    void theInstanceSaysMailIsEnabled() throws Exception {
        mockMvc.perform(get("/api/v1/instance")).andExpect(jsonPath("$.mailEnabled").value(true));
    }

    @Test
    void aLockedAccountIsSentItsLinkAgain() throws Exception {
        var address = newAddress();
        signUp(address, null);

        logIn(address)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_ACTIVATED"));
        verify(emailService, times(2)).sendActivationMail(any());
    }

    @Test
    void aWrongPasswordLearnsNothingAboutALockedAccount() throws Exception {
        var address = newAddress();
        signUp(address, null);

        mockMvc.perform(post("/api/v1/users/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"emailAddress\":\"%s\",\"password\":\"wrong-password\"}".formatted(address)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        verify(emailService, times(1)).sendActivationMail(any());
    }

    @Test
    void anAccountAnAdministratorLockedStaysLockedWithoutMail() throws Exception {
        var address = newAddress();
        signUp(address, invitation(Duration.ofDays(1)).getId());
        userService.setUserActivation(userRepository.findByEmailAddress(address).getUserId(), false);

        logIn(address)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ACCOUNT_AWAITING_APPROVAL"));
        verify(emailService, never()).sendActivationMail(any());
    }

    @Test
    void lockingAnAccountThatAwaitsConfirmationTakesItsLinkAway() throws Exception {
        var address = newAddress();
        signUp(address, null);
        userService.setUserActivation(userRepository.findByEmailAddress(address).getUserId(), false);

        logIn(address)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ACCOUNT_AWAITING_APPROVAL"));
        verify(emailService, times(1)).sendActivationMail(any());
    }

    @Test
    void editingAnAccountThatAwaitsConfirmationKeepsItsLink() throws Exception {
        var address = newAddress();
        signUp(address, null);
        userService.updateUser(userRepository.findByEmailAddress(address).getUserId(), address, false, null);

        logIn(address)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_ACTIVATED"));
        verify(emailService, times(2)).sendActivationMail(any());
    }
}
