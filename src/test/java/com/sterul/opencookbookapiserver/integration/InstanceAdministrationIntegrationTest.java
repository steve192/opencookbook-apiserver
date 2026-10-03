package com.sterul.opencookbookapiserver.integration;

import static com.sterul.opencookbookapiserver.integration.TestAccounts.operator;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.entities.instance.SignupMode;
import com.sterul.opencookbookapiserver.repositories.InvitationRepository;
import com.sterul.opencookbookapiserver.repositories.UserRepository;
import com.sterul.opencookbookapiserver.services.instance.InstanceSettingsService;

import jakarta.mail.Multipart;
import jakarta.mail.internet.MimeMessage;

/**
 * The admin's view of an instance with mail, a recipe scan service and secrets configured, where
 * none of the services can be reached. The mail server is real until a test stubs sending.
 */
@SpringBootTest(properties = {
        "opencookbook.instanceURL=https://cookbook.example.com",
        "opencookbook.smtp-host=127.0.0.1",
        "opencookbook.smtp-port=1",
        "opencookbook.smtp-protocol=smtp",
        "opencookbook.smtp-username=" + InstanceAdministrationIntegrationTest.SMTP_USERNAME,
        "opencookbook.smtp-password=" + InstanceAdministrationIntegrationTest.SMTP_PASSWORD,
        "opencookbook.mail-from=cookpal@cookbook.example.com",
        "opencookbook.recipe-scaper-service-url=http://import-user:" + InstanceAdministrationIntegrationTest.IMPORT_PASSWORD + "@127.0.0.1:1",
        "opencookbook.ml.service-url=http://ml-user:" + InstanceAdministrationIntegrationTest.ML_PASSWORD + "@127.0.0.1:1",
        "opencookbook.ml.api-token=" + InstanceAdministrationIntegrationTest.ML_TOKEN,
        "opencookbook.ml.connect-timeout-seconds=1",
        "opencookbook.ml.request-timeout-seconds=1",
})
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
class InstanceAdministrationIntegrationTest extends IntegrationTestBase {

    static final String SMTP_USERNAME = "smtp-login-name";
    static final String SMTP_PASSWORD = "smtp-password-never-shown";
    static final String ML_TOKEN = "cpml_token_never_shown";
    static final String IMPORT_PASSWORD = "import-password-never-shown";
    static final String ML_PASSWORD = "ml-password-never-shown";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private InvitationRepository invitationRepository;
    @Autowired
    private InstanceSettingsService settings;

    @MockitoSpyBean
    private JavaMailSender mailSender;

    private CookpalUser administrator;

    @BeforeEach
    void setUp() {
        invitationRepository.deleteAll();
        administrator = TestInstance.setUp(userRepository);
    }

    @AfterEach
    void reopen() {
        settings.setSignupMode(SignupMode.OPEN);
    }

    @Test
    void theOverviewSaysWhatIsConfigured() throws Exception {
        mockMvc.perform(get("/api/v1/admin/instance").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instanceUrl.configured").value("https://cookbook.example.com"))
                .andExpect(jsonPath("$.instanceUrl.effective").value("https://cookbook.example.com"))
                .andExpect(jsonPath("$.registration.signupMode").value("OPEN"))
                .andExpect(jsonPath("$.registration.confirmation").value("MAIL"))
                .andExpect(jsonPath("$.mail.configured").value(true))
                .andExpect(jsonPath("$.mail.enabled").value(true))
                .andExpect(jsonPath("$.mail.host").value("127.0.0.1"))
                .andExpect(jsonPath("$.mail.port").value(1))
                .andExpect(jsonPath("$.mail.protocol").value("smtp"))
                .andExpect(jsonPath("$.mail.startTls").value(false))
                .andExpect(jsonPath("$.mail.from").value("cookpal@cookbook.example.com"))
                .andExpect(jsonPath("$.features.sharing").value(true))
                .andExpect(jsonPath("$.features.households").value(true))
                .andExpect(jsonPath("$.features.apiKeys").value(true))
                .andExpect(jsonPath("$.features.recipeScan.configured").value(true))
                .andExpect(jsonPath("$.features.recipeScan.enabled").value(true))
                .andExpect(jsonPath("$.services.recipeImportUrl").value("http://127.0.0.1:1"))
                .andExpect(jsonPath("$.services.recipeScanUrl").value("http://127.0.0.1:1"))
                .andExpect(jsonPath("$.legal.directory").value("/opencookbook/legal"))
                .andExpect(jsonPath("$.legal.documents", hasSize(3)))
                .andExpect(jsonPath("$.legal.documents[0].document").value("terms"))
                .andExpect(jsonPath("$.legal.documents[0].published").value(false))
                .andExpect(jsonPath("$.limits.maxUploadMb").value(30))
                .andExpect(jsonPath("$.limits.maxImageMb").value(10));
    }

    @Test
    void theOverviewNeverCarriesASecret() throws Exception {
        var body = mockMvc.perform(get("/api/v1/admin/instance").with(admin()))
                .andReturn().getResponse().getContentAsString();

        assertFalse(body.contains(SMTP_PASSWORD), "the SMTP password was shown");
        assertFalse(body.contains(SMTP_USERNAME), "the SMTP username was shown");
        assertFalse(body.contains(ML_TOKEN), "the ML token was shown");
        assertFalse(body.contains(IMPORT_PASSWORD), "the recipe import login was shown");
        assertFalse(body.contains(ML_PASSWORD), "the recipe scan login was shown");
    }

    @Test
    void servicesThatAreDownFailTheirChecksWithAReason() throws Exception {
        mockMvc.perform(get("/api/v1/admin/instance/checks").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].check").value("MAIL"))
                .andExpect(jsonPath("$[0].status").value("FAILED"))
                .andExpect(jsonPath("$[0].detail").value(containsString("Connection refused")))
                .andExpect(jsonPath("$[1].check").value("RECIPE_IMPORT"))
                .andExpect(jsonPath("$[1].status").value("FAILED"))
                .andExpect(jsonPath("$[1].detail").isString())
                .andExpect(jsonPath("$[2].check").value("RECIPE_SCAN"))
                .andExpect(jsonPath("$[2].status").value("FAILED"))
                .andExpect(jsonPath("$[2].detail").isString());
    }

    @Test
    void aTestMailGoesToTheSignedInAdministrator() throws Exception {
        doNothing().when(mailSender).send(any(MimeMessage.class));

        mockMvc.perform(post("/api/v1/admin/instance/test-mail").with(admin()))
                .andExpect(status().isNoContent());

        var sent = sentMail();
        assertEquals(administrator.getEmailAddress(), sent.getAllRecipients()[0].toString());
    }

    @Test
    void aFailedTestMailSaysWhatTheMailServerSaid() throws Exception {
        mockMvc.perform(post("/api/v1/admin/instance/test-mail").with(admin()))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("MAIL_DELIVERY_FAILED"))
                .andExpect(jsonPath("$.message").value(containsString("Connection refused")));
    }

    @Test
    void anInvitationCanBeMailed() throws Exception {
        doNothing().when(mailSender).send(any(MimeMessage.class));

        mockMvc.perform(post("/api/v1/admin/invitations").with(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"validForDays\":30,\"sendTo\":\"friend@example.com\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.link").value(startsWith("https://cookbook.example.com/app/invite/")));

        var sent = sentMail();
        assertEquals("friend@example.com", sent.getAllRecipients()[0].toString());
        var link = "https://cookbook.example.com/app/invite/" + invitationRepository.findAll().get(0).getId();
        assertTrue(textOf(sent.getContent()).contains(link), "the mail does not carry the link");
        assertTrue(textOf(sent.getContent()).contains("30 Tage"), "the mail does not say how long the link works");
    }

    @Test
    void anInvitationWhoseMailFailsIsNotCreated() throws Exception {
        mockMvc.perform(post("/api/v1/admin/invitations").with(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sendTo\":\"friend@example.com\"}"))
                .andExpect(status().isBadGateway());

        assertEquals(0, invitationRepository.count());
    }

    @Test
    void aPasswordResetIsMailedAndShown() throws Exception {
        doNothing().when(mailSender).send(any(MimeMessage.class));
        var user = TestAccounts.ensure(userRepository, "forgetful@example.com");

        mockMvc.perform(post("/api/v1/admin/users/" + user.getUserId() + "/password-reset").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.link").value(startsWith("https://cookbook.example.com/app/resetPassword?id=")))
                .andExpect(jsonPath("$.mailed").value(true));
        assertEquals("forgetful@example.com", sentMail().getAllRecipients()[0].toString());
    }

    @Test
    void aPasswordResetWhoseMailFailsIsStillShown() throws Exception {
        var user = TestAccounts.ensure(userRepository, "forgetful@example.com");

        mockMvc.perform(post("/api/v1/admin/users/" + user.getUserId() + "/password-reset").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.link").value(startsWith("https://cookbook.example.com/app/resetPassword?id=")))
                .andExpect(jsonPath("$.mailed").value(false));
    }

    @Test
    void theSignupModeIsChangedInTheSettings() throws Exception {
        mockMvc.perform(get("/api/v1/admin/settings").with(admin()))
                .andExpect(jsonPath("$.signupMode").value("OPEN"));

        mockMvc.perform(put("/api/v1/admin/settings").with(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"signupMode\":\"INVITATION_ONLY\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.signupMode").value("INVITATION_ONLY"));

        mockMvc.perform(get("/api/v1/admin/settings").with(admin()))
                .andExpect(jsonPath("$.signupMode").value("INVITATION_ONLY"));
        mockMvc.perform(get("/api/v1/instance"))
                .andExpect(jsonPath("$.signupMode").value("INVITATION_ONLY"));
    }

    @Test
    void aSignupModeThatDoesNotExistIsRefused() throws Exception {
        mockMvc.perform(put("/api/v1/admin/settings").with(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"signupMode\":null}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/v1/admin/settings").with(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"signupMode\":\"CLOSED\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void anOrdinaryAccountCannotSeeTheInstance() throws Exception {
        mockMvc.perform(get("/api/v1/admin/instance").with(user("someone@example.com")))
                .andExpect(status().isForbidden());
    }

    private RequestPostProcessor admin() {
        return operator(administrator.getEmailAddress());
    }

    private MimeMessage sentMail() {
        var captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        return captor.getValue();
    }

    /** Decoded, as a mail client shows it: German text travels quoted-printable. */
    private static String textOf(Object content) throws Exception {
        if (content instanceof String text) {
            return text;
        }
        var all = new StringBuilder();
        if (content instanceof Multipart multipart) {
            for (var part = 0; part < multipart.getCount(); part++) {
                all.append(textOf(multipart.getBodyPart(part).getContent()));
            }
        }
        return all.toString();
    }
}
