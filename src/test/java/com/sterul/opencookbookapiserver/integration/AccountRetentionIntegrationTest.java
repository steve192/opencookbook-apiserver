package com.sterul.opencookbookapiserver.integration;

import static org.hamcrest.Matchers.contains;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;
import com.sterul.opencookbookapiserver.cronjobs.AccountRetentionJob;
import com.sterul.opencookbookapiserver.entities.account.ApiScope;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.entities.account.Role;
import com.sterul.opencookbookapiserver.repositories.UserRepository;
import com.sterul.opencookbookapiserver.services.UserService;
import com.sterul.opencookbookapiserver.services.apikeys.ApiKeyService;
import com.sterul.opencookbookapiserver.unit.MovableClock;

import jakarta.mail.internet.MimeMessage;

/** Recording use, and warning and deleting accounts nobody uses, on an instance that sends mail. */
@SpringBootTest(properties = {
        "opencookbook.smtp-host=smtp.cookpal.invalid",
        "opencookbook.instanceURL=https://cookpal.invalid",
        "opencookbook.mail-from=cookpal@cookpal.invalid",
        "opencookbook.retention.inactive-account-months=6"
})
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
class AccountRetentionIntegrationTest extends IntegrationTestBase {

    private static final String PASSWORD = "a-password";
    private static final String NOTICE = "Dein CookPal Account wird bald gelöscht";
    private static final String DELETED = "Dein CookPal Account wurde gelöscht";
    // The database keeps microseconds; whole seconds compare equal after a round trip.
    private static final Instant START = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    private static final MovableClock CLOCK = new MovableClock(START);

    @TestBean
    private Clock clock;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private UserService userService;
    @Autowired
    private ApiKeyService apiKeyService;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private AccountRetentionJob job;
    @MockitoBean
    private JavaMailSender javaMailSender;

    static Clock clock() {
        return CLOCK;
    }

    @BeforeEach
    void setUp() {
        CLOCK.moveTo(START);
        TestInstance.setUp(userRepository);
        when(javaMailSender.createMimeMessage()).thenAnswer(call -> new JavaMailSenderImpl().createMimeMessage());
    }

    @Test
    void aSignInAndALaterRenewalAreRecorded() throws Exception {
        var account = recreate("activity@cookpal.invalid");
        var refreshToken = login("activity@cookpal.invalid");
        assertEquals(START, reload(account).getLastSignInAt());
        assertEquals(START, reload(account).getLastActiveAt());

        CLOCK.advanceBy(Duration.ofMinutes(10));
        refreshToken = renew(refreshToken);
        assertEquals(START, reload(account).getLastActiveAt(), "use within the hour is not written again");

        CLOCK.advanceBy(Duration.ofHours(1));
        renew(refreshToken);
        assertEquals(CLOCK.instant(), reload(account).getLastActiveAt());
        assertEquals(START, reload(account).getLastSignInAt());
    }

    @Test
    void usingAnApiKeyIsRecorded() throws Exception {
        var account = recreate("home-assistant@cookpal.invalid");
        var key = apiKeyService.create(account, "Home Assistant", Set.of(ApiScope.SHOPPING_READ)).secret();

        mockMvc.perform(get("/api/v1/api-keys/current").with(TestAccounts.bearer(key))).andExpect(status().isOk());

        assertEquals(START, reload(account).getLastActiveAt());
    }

    @Test
    void anUnusedAccountIsWarnedTwiceAndThenDeleted() throws Exception {
        var account = recreate("unused@cookpal.invalid");
        login("unused@cookpal.invalid");
        var due = today().plusMonths(6);

        enforceOn(due.minusDays(15));
        assertEquals(List.of(), subjectsSentTo("unused@cookpal.invalid"));

        enforceOn(due.minusDays(14));
        job.enforceRetention();
        assertEquals(List.of(NOTICE), subjectsSentTo("unused@cookpal.invalid"));

        enforceOn(due.minusDays(7));
        enforceOn(due.minusDays(1));
        assertEquals(List.of(NOTICE, NOTICE), subjectsSentTo("unused@cookpal.invalid"));
        assertTrue(userRepository.existsById(account.getUserId()));

        enforceOn(due);
        assertFalse(userRepository.existsById(account.getUserId()));
        assertEquals(List.of(NOTICE, NOTICE, DELETED), subjectsSentTo("unused@cookpal.invalid"));
    }

    @Test
    void signingInAfterANoticeStopsTheCountdown() throws Exception {
        var account = recreate("returning@cookpal.invalid");
        login("returning@cookpal.invalid");
        var due = today().plusMonths(6);

        enforceOn(due.minusDays(14));
        login("returning@cookpal.invalid");
        assertEquals(0, reload(account).getInactivityNotices());

        enforceOn(due.minusDays(7));
        enforceOn(due.plusDays(7));
        assertTrue(userRepository.existsById(account.getUserId()));
        assertEquals(List.of(NOTICE), subjectsSentTo("returning@cookpal.invalid"));
    }

    @Test
    void administratorsAndDemoAccountsAreKept() throws Exception {
        var demo = recreate("demo@cookpal.invalid");
        demo.setRoles(Role.DEMO);
        userRepository.save(demo);

        enforceOn(today().plusYears(2));

        assertTrue(userRepository.existsById(demo.getUserId()));
        assertTrue(userRepository.existsByEmailAddress(TestInstance.ADMINISTRATOR));
        assertEquals(List.of(), subjectsSentTo("demo@cookpal.invalid"));
    }

    @Test
    void anUnconfirmedAccountIsDeletedWithoutAnyMail() throws Exception {
        var unconfirmed = userService.createUser("unconfirmed@cookpal.invalid", PASSWORD, false, null);
        userService.createActivationLink(unconfirmed);
        var due = LocalDate.ofInstant(unconfirmed.getCreatedOn(), ZoneOffset.UTC).plusMonths(6);

        enforceOn(due.minusDays(1));
        assertTrue(userRepository.existsById(unconfirmed.getUserId()));

        enforceOn(due);
        assertFalse(userRepository.existsById(unconfirmed.getUserId()));
        assertEquals(List.of(), subjectsSentTo("unconfirmed@cookpal.invalid"));
    }

    @Test
    void theAdministratorSeesActivityAndHowAnAccountSignsIn() throws Exception {
        recreate("listed@cookpal.invalid");
        login("listed@cookpal.invalid");
        var row = "$[?(@.emailAddress == 'listed@cookpal.invalid')]";

        mockMvc.perform(get("/api/v1/admin/users").with(TestAccounts.operator(TestInstance.ADMINISTRATOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath(row + ".lastActiveAt").value(contains(START.toString())))
                .andExpect(jsonPath(row + ".lastSignInAt").value(contains(START.toString())))
                .andExpect(jsonPath(row + ".signInMethods[*]").value(contains("PASSWORD")));
        mockMvc.perform(get("/api/v1/admin/instance").with(TestAccounts.operator(TestInstance.ADMINISTRATOR)))
                .andExpect(jsonPath("$.retention.inactiveAccountMonths").value(6));
    }

    private CookpalUser recreate(String emailAddress) {
        return TestAccounts.recreate(userRepository, emailAddress, passwordEncoder.encode(PASSWORD));
    }

    private CookpalUser reload(CookpalUser account) {
        return userRepository.findById(account.getUserId()).orElseThrow();
    }

    private static LocalDate today() {
        return LocalDate.ofInstant(START, ZoneOffset.UTC);
    }

    /** The job's run on that day, at the time it is scheduled for. */
    private void enforceOn(LocalDate day) {
        CLOCK.moveTo(day.atTime(4, 0).toInstant(ZoneOffset.UTC));
        job.enforceRetention();
    }

    /** @return the refresh token */
    private String login(String emailAddress) throws Exception {
        var answer = mockMvc.perform(post("/api/v1/users/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"emailAddress\":\"%s\",\"password\":\"%s\"}".formatted(emailAddress, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(answer, "$.refreshToken");
    }

    /** @return the refresh token that replaces the one given */
    private String renew(String refreshToken) throws Exception {
        var answer = mockMvc.perform(post("/api/v1/users/refreshToken").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"%s\"}".formatted(refreshToken)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(answer, "$.refreshToken");
    }

    private List<String> subjectsSentTo(String emailAddress) throws Exception {
        var sent = ArgumentCaptor.forClass(MimeMessage.class);
        verify(javaMailSender, atLeast(0)).send(sent.capture());
        var subjects = new ArrayList<String>();
        for (var message : sent.getAllValues()) {
            if (message.getAllRecipients()[0].toString().equals(emailAddress)) {
                subjects.add(message.getSubject());
            }
        }
        return subjects;
    }
}
