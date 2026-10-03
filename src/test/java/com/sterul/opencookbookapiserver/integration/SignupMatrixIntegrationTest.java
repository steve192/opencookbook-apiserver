package com.sterul.opencookbookapiserver.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.sterul.opencookbookapiserver.entities.account.Invitation;
import com.sterul.opencookbookapiserver.entities.instance.SignupMode;
import com.sterul.opencookbookapiserver.repositories.InvitationRepository;
import com.sterul.opencookbookapiserver.repositories.UserRepository;
import com.sterul.opencookbookapiserver.services.EmailService;
import com.sterul.opencookbookapiserver.services.SecretTokens;
import com.sterul.opencookbookapiserver.services.SignupState;
import com.sterul.opencookbookapiserver.services.instance.InstanceSettingsService;

/**
 * Signing up in either registration mode, with and without an invitation. Run once on an instance
 * with mail and once without; only an open signup's outcome differs between the two.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
abstract class SignupMatrixIntegrationTest extends IntegrationTestBase {

    @Autowired
    protected MockMvc mockMvc;
    @Autowired
    protected UserRepository userRepository;
    @Autowired
    private InvitationRepository invitationRepository;
    @Autowired
    private InstanceSettingsService settings;

    @MockitoBean
    protected EmailService emailService;

    /** What an open signup without an invitation leads to on this instance. */
    abstract SignupState openSignupState();

    @BeforeEach
    void setUpInstance() {
        TestInstance.setUp(userRepository);
    }

    @AfterEach
    void reopen() {
        settings.setSignupMode(SignupMode.OPEN);
    }

    @Test
    void anOpenSignupWaitsLocked() throws Exception {
        var address = newAddress();

        signUp(address, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value(openSignupState().name()));
        assertFalse(userRepository.findByEmailAddress(address).isActivated());
    }

    @Test
    void anInvitationOnlyInstanceRefusesASignupWithoutAnInvitation() throws Exception {
        settings.setSignupMode(SignupMode.INVITATION_ONLY);
        var address = newAddress();

        signUp(address, null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SIGNUP_DISABLED"));
        assertNull(userRepository.findByEmailAddress(address));
    }

    @ParameterizedTest
    @EnumSource(SignupMode.class)
    void aValidInvitationGivesAnActiveAccountAndIsUsedUp(SignupMode mode) throws Exception {
        settings.setSignupMode(mode);
        var invitation = invitation(Duration.ofDays(7));
        var address = newAddress();

        signUp(address, invitation.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("ACTIVE"));
        assertTrue(userRepository.findByEmailAddress(address).isActivated());
        assertTrue(invitationRepository.findById(invitation.getId()).isEmpty());
    }

    @ParameterizedTest
    @EnumSource(SignupMode.class)
    void aUsedInvitationIsRefused(SignupMode mode) throws Exception {
        settings.setSignupMode(mode);
        var invitation = invitation(Duration.ofDays(7));
        signUp(newAddress(), invitation.getId()).andExpect(status().isOk());

        var address = newAddress();
        signUp(address, invitation.getId())
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("INVITATION_INVALID"));
        assertNull(userRepository.findByEmailAddress(address));
    }

    @ParameterizedTest
    @EnumSource(SignupMode.class)
    void anExpiredInvitationIsRefused(SignupMode mode) throws Exception {
        settings.setSignupMode(mode);
        var invitation = invitation(Duration.ofMinutes(-1));

        signUp(newAddress(), invitation.getId())
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("INVITATION_INVALID"));
    }

    @ParameterizedTest
    @EnumSource(SignupMode.class)
    void anUnknownInvitationIsRefused(SignupMode mode) throws Exception {
        settings.setSignupMode(mode);

        signUp(newAddress(), "no-such-invitation")
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("INVITATION_INVALID"));
    }

    @Test
    void anAddressAlreadyTakenLeavesTheInvitationUnused() throws Exception {
        var invitation = invitation(Duration.ofDays(7));

        signUp(TestInstance.ADMINISTRATOR, invitation.getId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));
        assertTrue(invitationRepository.findById(invitation.getId()).isPresent());
    }

    @Test
    void aBlankInvitationIsAnOpenSignup() throws Exception {
        signUp(newAddress(), "   ")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value(openSignupState().name()));
    }

    @Test
    void ofSimultaneousSignupsWithOneInvitationExactlyOneSucceeds() throws Exception {
        var invitation = invitation(Duration.ofDays(7));
        var signups = 4;
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(signups);
        try {
            var answers = IntStream.range(0, signups)
                    .mapToObj(signup -> executor.submit((Callable<Integer>) () -> {
                        start.await();
                        return signUp(newAddress(), invitation.getId()).andReturn().getResponse().getStatus();
                    }))
                    .toList();
            start.countDown();

            var statuses = new ArrayList<Integer>();
            for (var answer : answers) {
                statuses.add(answer.get());
            }
            assertEquals(1, statuses.stream().filter(answer -> answer == 200).count(), statuses.toString());
            assertEquals(signups - 1, statuses.stream().filter(answer -> answer == 410).count(),
                    statuses.toString());
        } finally {
            executor.shutdownNow();
        }
    }

    protected ResultActions signUp(String emailAddress, String invitation) throws Exception {
        var token = invitation == null ? "null" : "\"" + invitation + "\"";
        return mockMvc.perform(post("/api/v1/users/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"emailAddress\":\"%s\",\"password\":\"a-password\",\"invitation\":%s}"
                        .formatted(emailAddress, token)));
    }

    protected ResultActions logIn(String emailAddress) throws Exception {
        return mockMvc.perform(post("/api/v1/users/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"emailAddress\":\"%s\",\"password\":\"a-password\"}".formatted(emailAddress)));
    }

    protected static String newAddress() {
        return "signup-" + UUID.randomUUID() + "@example.com";
    }

    protected Invitation invitation(Duration validFor) {
        return invitationRepository.save(Invitation.builder()
                .id(SecretTokens.generate())
                .expiresAt(Instant.now().plus(validFor))
                .build());
    }
}
