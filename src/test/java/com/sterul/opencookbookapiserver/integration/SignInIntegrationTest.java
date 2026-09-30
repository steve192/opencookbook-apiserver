package com.sterul.opencookbookapiserver.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.entities.account.PasswordResetLink;
import com.sterul.opencookbookapiserver.entities.account.Role;
import com.sterul.opencookbookapiserver.repositories.PasswordResetLinkRepository;
import com.sterul.opencookbookapiserver.repositories.RefreshTokenRepository;
import com.sterul.opencookbookapiserver.repositories.UserRepository;
import com.sterul.opencookbookapiserver.services.AccessTokenService;
import com.sterul.opencookbookapiserver.services.SignInService;
import com.sterul.opencookbookapiserver.services.SecretTokens;
import com.sterul.opencookbookapiserver.services.UserService;

/** Signing in, renewing and signing out, and everything that has to end a sign in. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
class SignInIntegrationTest extends IntegrationTestBase {

    private static final String ANNA = "signin-anna@example.invalid";
    private static final String PASSWORD = "anna's password";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RefreshTokenRepository refreshTokenRepository;
    @Autowired
    private PasswordResetLinkRepository passwordResetLinkRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private UserService userService;
    @Autowired
    private SignInService signInService;
    @Autowired
    private AccessTokenService accessTokenService;

    private CookpalUser anna;

    @BeforeEach
    void setup() {
        refreshTokenRepository.deleteAll();
        anna = TestAccounts.recreate(userRepository, ANNA, passwordEncoder.encode(PASSWORD));
    }

    @Test
    void anAccessTokenSignsInAsTheAccount() throws Exception {
        var tokens = login();

        mockMvc.perform(get("/api/v1/users/self").with(TestAccounts.bearer(tokens.access())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(ANNA))
                .andExpect(jsonPath("$.roles").isEmpty());
    }

    @Test
    void aBrokenAccessTokenIsRefusedButPublicEndpointsIgnoreIt() throws Exception {
        mockMvc.perform(get("/api/v1/users/self").with(TestAccounts.bearer("not-a-token")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        mockMvc.perform(get("/api/v1/instance").with(TestAccounts.bearer("not-a-token")))
                .andExpect(status().isOk());
    }

    @Test
    void refreshTokensAreKeptOnlyAsHashes() throws Exception {
        var tokens = login();

        var stored = refreshTokenRepository.findAll();
        assertThat(stored).hasSize(1);
        assertThat(stored.get(0).getTokenHash()).isEqualTo(SecretTokens.hash(tokens.refresh()))
                .isNotEqualTo(tokens.refresh());
    }

    @Test
    void aRotatingRenewalReplacesTheRefreshToken() throws Exception {
        var tokens = login();

        var renewed = renew(tokens.refresh(), true).andExpect(status().isOk());
        var next = JsonPath.<String>read(body(renewed), "$.refreshToken");

        assertThat(next).isNotEqualTo(tokens.refresh());
        mockMvc.perform(get("/api/v1/users/self").with(TestAccounts.bearer(JsonPath.read(body(renewed), "$.token"))))
                .andExpect(status().isOk());
        renew(next, true).andExpect(status().isOk());
    }

    @Test
    void twoRenewalsAtOnceDoNotEndTheSignIn() throws Exception {
        var tokens = login();

        renew(tokens.refresh(), true).andExpect(status().isOk());
        renew(tokens.refresh(), true).andExpect(status().isOk());
    }

    @Test
    void aReplacedTokenUsedAgainLaterEndsTheSignInForEverybody() throws Exception {
        var tokens = login();
        var next = JsonPath.<String>read(body(renew(tokens.refresh(), true)), "$.refreshToken");
        replacedAWhileAgo(tokens.refresh());

        renew(tokens.refresh(), true)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        renew(next, true).andExpect(status().isUnauthorized());
    }

    @Test
    void aReplacedTokenIsDeletedSoonYetStillEndsTheSignInWhenUsedAgain() throws Exception {
        var tokens = login();
        var next = JsonPath.<String>read(body(renew(tokens.refresh(), true)), "$.refreshToken");
        replacedAWhileAgo(tokens.refresh());

        assertThat(signInService.deleteStale()).isEqualTo(1);

        renew(tokens.refresh(), true).andExpect(status().isUnauthorized());
        renew(next, true).andExpect(status().isUnauthorized());
    }

    @Test
    void anOlderAppThatDoesNotRotateKeepsItsToken() throws Exception {
        var tokens = login();

        renew(tokens.refresh(), false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.refreshToken").doesNotExist());
        renew(tokens.refresh(), false).andExpect(status().isOk());
    }

    @Test
    void signingOutEndsTheSignIn() throws Exception {
        var tokens = login();
        var other = login();

        mockMvc.perform(post("/api/v1/users/logout").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + tokens.refresh() + "\"}"))
                .andExpect(status().isNoContent());

        renew(tokens.refresh(), true).andExpect(status().isUnauthorized());
        renew(other.refresh(), true).andExpect(status().isOk());
    }

    @Test
    void aNewPasswordEndsEveryOtherSignIn() throws Exception {
        var here = login();
        var elsewhere = login();

        mockMvc.perform(post("/api/v1/users/changePassword").with(TestAccounts.bearer(here.access()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"" + PASSWORD + "\",\"newPassword\":\"a new password\"}"))
                .andExpect(status().isOk());

        renew(here.refresh(), true).andExpect(status().isOk());
        renew(elsewhere.refresh(), true).andExpect(status().isUnauthorized());
    }

    @Test
    void aPasswordResetEndsEverySignIn() throws Exception {
        var tokens = login();
        var link = new PasswordResetLink();
        link.setUser(anna);
        link.setValidUntil(Instant.now().plus(1, ChronoUnit.HOURS));
        link = passwordResetLinkRepository.save(link);

        mockMvc.perform(post("/api/v1/users/resetPassword").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"passwordResetId\":\"" + link.getId() + "\",\"newPassword\":\"a new password\"}"))
                .andExpect(status().isOk());

        renew(tokens.refresh(), true).andExpect(status().isUnauthorized());
    }

    @Test
    void aDeactivatedAccountIsSignedOutAtOnce() throws Exception {
        var tokens = login();

        userService.setUserActivation(anna.getUserId(), false);

        mockMvc.perform(get("/api/v1/users/self").with(TestAccounts.bearer(tokens.access())))
                .andExpect(status().isUnauthorized());
        renew(tokens.refresh(), true).andExpect(status().isUnauthorized());
        assertThat(refreshTokenRepository.findAll()).isEmpty();
    }

    @Test
    void aDeletedAccountsTokenIsRefusedRatherThanFailing() throws Exception {
        var tokens = login();

        userService.deleteUser(userRepository.findByEmailAddress(ANNA));

        mockMvc.perform(get("/api/v1/users/self").with(TestAccounts.bearer(tokens.access())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void aNewAddressKeepsTheSignIn() throws Exception {
        var tokens = login();

        userService.updateUser(anna.getUserId(), "signin-anna-new@example.invalid", true, null);

        mockMvc.perform(get("/api/v1/users/self").with(TestAccounts.bearer(tokens.access())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("signin-anna-new@example.invalid"));
        userService.updateUser(anna.getUserId(), ANNA, true, null);
    }

    @Test
    void signInsUnusedForTooLongAreCleanedUp() throws Exception {
        login();
        var fresh = login();
        var stale = refreshTokenRepository.findAll().get(0);
        stale.setValidUntil(Instant.now().minus(Duration.ofDays(1)));
        refreshTokenRepository.save(stale);

        assertThat(signInService.deleteStale()).isEqualTo(1);
        renew(fresh.refresh(), true).andExpect(status().isOk());
    }

    @Test
    void anAdministratorWhoJustSignedInUsesTheAdminApi() throws Exception {
        makeAnna(Role.ADMIN);
        var tokens = login();

        mockMvc.perform(get("/api/v1/admin/users").with(TestAccounts.bearer(tokens.access())))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/users/self").with(TestAccounts.bearer(tokens.access())))
                .andExpect(jsonPath("$.roles[0]").value("ADMIN"));
    }

    @Test
    void anAdministratorIsAskedForThePasswordAgainAfterAWhile() throws Exception {
        makeAnna(Role.ADMIN);
        var longAgo = accessTokenService.issue(anna, "old-sign-in", Instant.now().minus(Duration.ofHours(3)));
        var byActivationLink = accessTokenService.issue(anna, "link-sign-in", null);

        for (var token : new String[] {longAgo, byActivationLink}) {
            mockMvc.perform(get("/api/v1/admin/users").with(TestAccounts.bearer(token)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("REAUTHENTICATION_REQUIRED"));
            mockMvc.perform(get("/api/v1/users/self").with(TestAccounts.bearer(token)))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void anybodyElseIsRefusedTheAdminApiRatherThanAskedToSignInAgain() throws Exception {
        var tokens = login();

        mockMvc.perform(get("/api/v1/admin/users").with(TestAccounts.bearer(tokens.access())))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void aDemoAccountCannotLockOthersOut() throws Exception {
        makeAnna(Role.DEMO);
        var tokens = login();

        mockMvc.perform(post("/api/v1/users/changePassword").with(TestAccounts.bearer(tokens.access()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"" + PASSWORD + "\",\"newPassword\":\"a new password\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/users/self").with(TestAccounts.bearer(tokens.access())))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/api-keys").with(TestAccounts.bearer(tokens.access()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"x\",\"scopes\":[\"shopping:read\"]}"))
                .andExpect(status().isForbidden());
        assertThat(userRepository.findByEmailAddress(ANNA)).isNotNull();
    }

    @Test
    void theAdminPanelMayOnlyRunWhatItShips() throws Exception {
        mockMvc.perform(get("/admin/"))
                .andExpect(header().string("Content-Security-Policy", containsString("script-src 'self'")));
        mockMvc.perform(get("/api/v1/instance"))
                .andExpect(header().doesNotExist("Content-Security-Policy"));
    }

    private record Tokens(String access, String refresh) {
    }

    private Tokens login() throws Exception {
        var answer = body(mockMvc.perform(post("/api/v1/users/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"emailAddress\":\"" + ANNA + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk()));
        return new Tokens(JsonPath.read(answer, "$.token"), JsonPath.read(answer, "$.refreshToken"));
    }

    private ResultActions renew(String refreshToken, boolean rotate) throws Exception {
        return mockMvc.perform(post("/api/v1/users/refreshToken").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refreshToken + "\",\"rotate\":" + rotate + "}"));
    }

    private void replacedAWhileAgo(String refreshToken) {
        var token = refreshTokenRepository.findByTokenHash(SecretTokens.hash(refreshToken)).orElseThrow();
        token.setReplacedAt(Instant.now().minus(Duration.ofMinutes(1)));
        refreshTokenRepository.save(token);
    }

    private void makeAnna(Role role) {
        anna.setRoles(role);
        anna = userRepository.save(anna);
    }

    private static String body(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString();
    }
}
