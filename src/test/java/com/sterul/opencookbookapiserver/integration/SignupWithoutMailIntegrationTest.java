package com.sterul.opencookbookapiserver.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import com.sterul.opencookbookapiserver.repositories.ActivationLinkRepository;
import com.sterul.opencookbookapiserver.services.SignupState;
import com.sterul.opencookbookapiserver.services.mail.MailAvailability;

/** No SMTP host: open signups wait for an administrator, and nothing promises a mail. */
class SignupWithoutMailIntegrationTest extends SignupMatrixIntegrationTest {

    @Autowired
    private ActivationLinkRepository activationLinkRepository;
    @MockitoSpyBean
    private MailAvailability mail;

    @Override
    SignupState openSignupState() {
        return SignupState.AWAITING_APPROVAL;
    }

    @Test
    void anOpenSignupSendsNothingButKeepsItsActivationLink() throws Exception {
        var before = activationLinkRepository.count();

        signUp(newAddress(), null).andExpect(status().isOk());

        verify(emailService, never()).sendActivationMail(any());
        assertEquals(before + 1, activationLinkRepository.count());
    }

    @Test
    void anAccountCreatedWithoutMailConfirmsOnceMailIsEnabled() throws Exception {
        var address = newAddress();
        signUp(address, null);
        doReturn(true).when(mail).isEnabled();

        logIn(address)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_ACTIVATED"));
        verify(emailService, times(1)).sendActivationMail(any());

        var link = activationLinkRepository.findAll().stream()
                .filter(candidate -> candidate.getUser().getEmailAddress().equals(address))
                .findFirst().orElseThrow();
        mockMvc.perform(get("/api/v1/users/activate").param("activationId", link.getId()))
                .andExpect(status().isOk());
        logIn(address).andExpect(status().isOk());
    }

    @Test
    void aLockedAccountIsToldItWaitsForAnAdministrator() throws Exception {
        var address = newAddress();
        signUp(address, null);

        logIn(address)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ACCOUNT_AWAITING_APPROVAL"));
        verify(emailService, never()).sendActivationMail(any());
    }

    @Test
    void aPasswordResetCannotBeMailed() throws Exception {
        mockMvc.perform(post("/api/v1/users/requestPasswordReset")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"emailAddress\":\"" + TestInstance.ADMINISTRATOR + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MAIL_NOT_CONFIGURED"));
        verify(emailService, never()).sendPasswordResetMail(any());
    }

    @Test
    void resendingAnActivationLinkDoesNothing() throws Exception {
        var address = newAddress();
        signUp(address, null);

        mockMvc.perform(post("/api/v1/users/resendActivationLink")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"emailAddress\":\"" + address + "\"}"))
                .andExpect(status().isOk());
        verify(emailService, never()).sendActivationMail(any());
    }
}
