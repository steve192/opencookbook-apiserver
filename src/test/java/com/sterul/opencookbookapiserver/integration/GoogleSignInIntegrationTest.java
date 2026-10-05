package com.sterul.opencookbookapiserver.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.sterul.opencookbookapiserver.entities.account.Invitation;
import com.sterul.opencookbookapiserver.entities.instance.SignupMode;
import com.sterul.opencookbookapiserver.errors.ApiErrorCode;
import com.sterul.opencookbookapiserver.errors.ApiException;
import com.sterul.opencookbookapiserver.repositories.ActivationLinkRepository;
import com.sterul.opencookbookapiserver.repositories.InvitationRepository;
import com.sterul.opencookbookapiserver.repositories.UserRepository;
import com.sterul.opencookbookapiserver.services.SecretTokens;
import com.sterul.opencookbookapiserver.services.UserService;
import com.sterul.opencookbookapiserver.services.google.GoogleIdTokens;
import com.sterul.opencookbookapiserver.services.google.GoogleIdTokens.VerifiedAddress;
import com.sterul.opencookbookapiserver.services.instance.InstanceSettingsService;

/** Signing in with Google on an instance that offers it. Google's side is stood in for. */
@SpringBootTest(properties = {
        "opencookbook.auth.google.client-id=" + GoogleSignInIntegrationTest.WEB_CLIENT,
        "opencookbook.auth.google.android-client-id=" + GoogleSignInIntegrationTest.ANDROID_CLIENT })
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
class GoogleSignInIntegrationTest extends IntegrationTestBase {

    static final String WEB_CLIENT = "web.apps.googleusercontent.com";
    static final String ANDROID_CLIENT = "android.apps.googleusercontent.com";
    private static final String ID_TOKEN = "an-id-token";
    private static final String PASSWORD = "a-password";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ActivationLinkRepository activationLinkRepository;
    @Autowired
    private InvitationRepository invitationRepository;
    @Autowired
    private UserService userService;
    @Autowired
    private InstanceSettingsService settings;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @MockitoBean
    private GoogleIdTokens idTokens;

    @BeforeEach
    void setUp() {
        TestInstance.setUp(userRepository);
    }

    @AfterEach
    void reopen() {
        settings.setSignupMode(SignupMode.OPEN);
    }

    @Test
    void aNewAddressGetsAnActiveAccountWithoutAPassword() throws Exception {
        var tokens = signInAs("new@gmail.com", null).andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/users/self").with(TestAccounts.bearer(accessToken(tokens))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("new@gmail.com"))
                .andExpect(jsonPath("$.onboarded").value(false));
        var account = userRepository.findByEmailAddress("new@gmail.com");
        assertTrue(account.isActivated());
        assertNull(account.getPasswordHash());
    }

    @Test
    void anExistingAccountIsTheOneSignedInToAndKeepsItsPassword() throws Exception {
        var existing = TestAccounts.recreate(userRepository, "existing@gmail.com", passwordEncoder.encode(PASSWORD));

        var tokens = signInAs("existing@gmail.com", null).andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/users/self").with(TestAccounts.bearer(accessToken(tokens))))
                .andExpect(jsonPath("$.email").value("existing@gmail.com"));
        assertEquals(existing.getUserId(), userRepository.findByEmailAddress("existing@gmail.com").getUserId());
        logInWithPassword("existing@gmail.com").andExpect(status().isOk());
    }

    @Test
    void anAddressGoogleDoesNotHostOpensNoExistingAccount() throws Exception {
        TestAccounts.recreate(userRepository, "existing@gmx.de", passwordEncoder.encode(PASSWORD));
        var waiting = userService.createUser("waiting@gmx.de", PASSWORD, false, null);
        userService.createActivationLink(waiting);

        signInAs(new VerifiedAddress("existing@gmx.de", false), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));
        signInAs(new VerifiedAddress("waiting@gmx.de", false), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));
        assertFalse(userRepository.findByEmailAddress("waiting@gmx.de").isActivated());
    }

    @Test
    void anAddressGoogleDoesNotHostStillGetsANewAccount() throws Exception {
        signInAs(new VerifiedAddress("new@gmx.de", false), null).andExpect(status().isOk());

        assertTrue(userRepository.findByEmailAddress("new@gmx.de").isActivated());
    }

    @Test
    void anInvitationOnlyInstanceRefusesAnAddressWithoutAnAccount() throws Exception {
        settings.setSignupMode(SignupMode.INVITATION_ONLY);

        signInAs("uninvited@gmail.com", null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SIGNUP_DISABLED"));
        assertFalse(userRepository.existsByEmailAddress("uninvited@gmail.com"));
    }

    @Test
    void anInvitationLetsTheAddressInOnce() throws Exception {
        settings.setSignupMode(SignupMode.INVITATION_ONLY);
        var invitation = invitationRepository.save(Invitation.builder()
                .id(SecretTokens.generate())
                .expiresAt(Instant.now().plus(Duration.ofDays(1)))
                .build());

        signInAs("invited@gmail.com", invitation.getId()).andExpect(status().isOk());

        assertTrue(userRepository.findByEmailAddress("invited@gmail.com").isActivated());
        assertTrue(invitationRepository.findById(invitation.getId()).isEmpty());
    }

    @Test
    void anAccountWaitingForItsConfirmationIsActivatedAndLosesTheUnprovenPassword() throws Exception {
        var waiting = userService.createUser("waiting@gmail.com", PASSWORD, false, null);
        userService.createActivationLink(waiting);

        signInAs("waiting@gmail.com", null).andExpect(status().isOk());

        var account = userRepository.findByEmailAddress("waiting@gmail.com");
        assertTrue(account.isActivated());
        assertNull(account.getPasswordHash());
        assertFalse(activationLinkRepository.existsByUser(account));
    }

    @Test
    void anAccountAnAdministratorLockedStaysLocked() throws Exception {
        var locked = TestAccounts.recreate(userRepository, "locked@gmail.com", passwordEncoder.encode(PASSWORD));
        locked.setActivated(false);
        userRepository.save(locked);

        signInAs("locked@gmail.com", null)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ACCOUNT_AWAITING_APPROVAL"));
        assertFalse(userRepository.findByEmailAddress("locked@gmail.com").isActivated());
    }

    @Test
    void anAccountWithoutAPasswordCannotSignInWithOne() throws Exception {
        signInAs("google-only@gmail.com", null).andExpect(status().isOk());

        logInWithPassword("google-only@gmail.com")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void theAdminApiStillAsksForThePassword() throws Exception {
        var tokens = signInAs(TestInstance.ADMINISTRATOR, null).andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/admin/users").with(TestAccounts.bearer(accessToken(tokens))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REAUTHENTICATION_REQUIRED"));
    }

    @Test
    void aTokenGoogleDoesNotVouchForIsRefused() throws Exception {
        when(idTokens.verifiedAddress(ID_TOKEN)).thenThrow(new ApiException(ApiErrorCode.GOOGLE_SIGN_IN_FAILED));

        signIn(null)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("GOOGLE_SIGN_IN_FAILED"));
    }

    @Test
    void theInstanceNamesItsClientsToTheAppAndTheAdministrator() throws Exception {
        mockMvc.perform(get("/api/v1/instance"))
                .andExpect(jsonPath("$.googleSignIn.clientId").value(WEB_CLIENT))
                .andExpect(jsonPath("$.googleSignIn.androidClientId").value(ANDROID_CLIENT));
        mockMvc.perform(get("/api/v1/admin/instance").with(TestAccounts.operator(TestInstance.ADMINISTRATOR)))
                .andExpect(jsonPath("$.googleSignIn.clientId").value(WEB_CLIENT))
                .andExpect(jsonPath("$.googleSignIn.androidClientId").value(ANDROID_CLIENT));
    }

    private ResultActions signInAs(String emailAddress, String invitation) throws Exception {
        return signInAs(new VerifiedAddress(emailAddress, true), invitation);
    }

    private ResultActions signInAs(VerifiedAddress address, String invitation) throws Exception {
        when(idTokens.verifiedAddress(ID_TOKEN)).thenReturn(address);
        return signIn(invitation);
    }

    private ResultActions signIn(String invitation) throws Exception {
        var body = invitation == null
                ? "{\"idToken\":\"%s\"}".formatted(ID_TOKEN)
                : "{\"idToken\":\"%s\",\"invitation\":\"%s\"}".formatted(ID_TOKEN, invitation);
        return mockMvc.perform(post("/api/v1/users/login/google").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions logInWithPassword(String emailAddress) throws Exception {
        return mockMvc.perform(post("/api/v1/users/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"emailAddress\":\"%s\",\"password\":\"%s\"}".formatted(emailAddress, PASSWORD)));
    }

    private static String accessToken(ResultActions signedIn) throws Exception {
        return JsonPath.read(signedIn.andReturn().getResponse().getContentAsString(), "$.token");
    }
}
