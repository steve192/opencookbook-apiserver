package com.sterul.opencookbookapiserver.integration;

import static com.sterul.opencookbookapiserver.integration.TestAccounts.operator;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.jayway.jsonpath.JsonPath;
import com.sterul.opencookbookapiserver.cronjobs.AccountRetentionJob;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.repositories.UserRepository;
import com.sterul.opencookbookapiserver.services.EmailService;
import com.sterul.opencookbookapiserver.services.UserService;

/** The admin's view of an instance with nothing but the defaults: no mail, no instance address, no services. */
@SpringBootTest(properties = {
        "opencookbook.recipe-scaper-service-url=",
        "opencookbook.retention.inactive-account-months=6"
})
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
class InstanceWithoutMailIntegrationTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private UserService userService;
    @Autowired
    private AccountRetentionJob retentionJob;

    @MockitoBean
    private EmailService emailService;

    @BeforeEach
    void setUp() {
        TestInstance.setUp(userRepository);
    }

    @Test
    void theOverviewSaysAdministratorsApproveSignups() throws Exception {
        mockMvc.perform(get("/api/v1/admin/instance").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instanceUrl.configured").value(nullValue()))
                .andExpect(jsonPath("$.instanceUrl.effective").value("http://localhost"))
                .andExpect(jsonPath("$.registration.confirmation").value("ADMIN_APPROVAL"))
                .andExpect(jsonPath("$.mail.configured").value(false))
                .andExpect(jsonPath("$.mail.enabled").value(false))
                .andExpect(jsonPath("$.mail.port").value(nullValue()))
                .andExpect(jsonPath("$.mail.protocol").value(nullValue()))
                .andExpect(jsonPath("$.mail.startTls").value(nullValue()))
                .andExpect(jsonPath("$.mail.host").value(nullValue()))
                .andExpect(jsonPath("$.features.recipeScan.configured").value(false))
                .andExpect(jsonPath("$.services.recipeImportUrl").value(nullValue()))
                .andExpect(jsonPath("$.services.recipeScanUrl").value(nullValue()));
    }

    @Test
    void nothingConfiguredIsNothingChecked() throws Exception {
        mockMvc.perform(get("/api/v1/admin/instance/checks").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].status", everyItem(is("NOT_CONFIGURED"))));
    }

    @Test
    void aTestMailNeedsMail() throws Exception {
        mockMvc.perform(post("/api/v1/admin/instance/test-mail").with(admin()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MAIL_NOT_CONFIGURED"));
    }

    @Test
    void aPasswordResetLinkIsHandedOverAndWorks() throws Exception {
        var user = TestAccounts.recreate(userRepository, "forgetful@example.com", "irrelevant");

        var body = mockMvc.perform(post("/api/v1/admin/users/" + user.getUserId() + "/password-reset").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.link").value(startsWith("http://localhost/app/resetPassword?id=")))
                .andExpect(jsonPath("$.mailed").value(false))
                .andReturn().getResponse().getContentAsString();
        String link = JsonPath.read(body, "$.link");

        mockMvc.perform(post("/api/v1/users/resetPassword")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"passwordResetId\":\"%s\",\"newPassword\":\"a-new-password\"}"
                        .formatted(link.substring(link.indexOf("id=") + 3))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/users/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"emailAddress\":\"forgetful@example.com\",\"password\":\"a-new-password\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void deletingAnAccountMailsNobody() throws Exception {
        var user = TestAccounts.recreate(userRepository, "leaving@example.com", "irrelevant");

        userService.deleteUser(user);

        verify(emailService, never()).sendAccountDeletedMail(any(), any(), anyBoolean());
    }

    @Test
    void anUnusedAccountThatCouldBeWarnedIsKeptAndALockedOneDeleted() throws Exception {
        var active = unusedSinceLastYear(TestAccounts.recreate(userRepository, "unused@example.com", "irrelevant"));
        var locked = TestAccounts.recreate(userRepository, "unused-locked@example.com", "irrelevant");
        locked.setActivated(false);
        unusedSinceLastYear(locked);

        retentionJob.enforceRetention();

        assertTrue(userRepository.existsById(active.getUserId()));
        assertFalse(userRepository.existsById(locked.getUserId()));
        verify(emailService, never()).sendInactivityNotice(any(), any());
    }

    private CookpalUser unusedSinceLastYear(CookpalUser user) {
        user.setLastActiveAt(Instant.now().minus(Duration.ofDays(400)));
        return userRepository.save(user);
    }

    private static RequestPostProcessor admin() {
        return operator(TestInstance.ADMINISTRATOR);
    }
}
