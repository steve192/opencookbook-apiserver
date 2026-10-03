package com.sterul.opencookbookapiserver.integration;

import static com.sterul.opencookbookapiserver.integration.TestAccounts.operator;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.jayway.jsonpath.JsonPath;
import com.sterul.opencookbookapiserver.cronjobs.InvitationDeletionJob;
import com.sterul.opencookbookapiserver.entities.account.Invitation;
import com.sterul.opencookbookapiserver.repositories.InvitationRepository;
import com.sterul.opencookbookapiserver.repositories.UserRepository;
import com.sterul.opencookbookapiserver.services.SecretTokens;

/** Invitation links as an administrator hands them out, on an instance without mail. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
class InvitationApiIntegrationTest extends IntegrationTestBase {

    private static final String INVITATIONS = "/api/v1/admin/invitations";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private InvitationRepository invitationRepository;
    @Autowired
    private InvitationDeletionJob deletionJob;

    @BeforeEach
    void setUp() {
        invitationRepository.deleteAll();
        TestInstance.setUp(userRepository);
    }

    @Test
    void anInvitationIsALinkIntoTheAppValidForAWeekByDefault() throws Exception {
        var body = create("{}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.createdBy").value(TestInstance.ADMINISTRATOR))
                .andExpect(jsonPath("$.createdOn").isString())
                .andReturn().getResponse().getContentAsString();

        String id = JsonPath.read(body, "$.id");
        assertEquals("http://localhost/app/invite/" + id, JsonPath.read(body, "$.link"));
        assertEquals(43, id.length());
        assertExpiresIn(Duration.ofDays(7), body);
    }

    @ParameterizedTest
    @ValueSource(ints = { 1, 7, 30 })
    void theOfferedValiditiesAreAccepted(int days) throws Exception {
        var body = create("{\"validForDays\":" + days + "}")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        assertExpiresIn(Duration.ofDays(days), body);
    }

    @ParameterizedTest
    @ValueSource(ints = { 0, 2, 365 })
    void anyOtherValidityIsRefused(int days) throws Exception {
        create("{\"validForDays\":" + days + "}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void aBlankAddressMeansNoMail() throws Exception {
        create("{\"sendTo\":\"  \"}").andExpect(status().isCreated());
    }

    @Test
    void sendingByMailNeedsMail() throws Exception {
        create("{\"sendTo\":\"friend@example.com\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MAIL_NOT_CONFIGURED"));
        assertEquals(0, invitationRepository.count());
    }

    @Test
    void theListShowsOnlyOpenInvitations() throws Exception {
        var open = idOf(create("{}"));
        expired();

        mockMvc.perform(get(INVITATIONS).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(open))
                .andExpect(jsonPath("$[0].link").value("http://localhost/app/invite/" + open));
    }

    @Test
    void aRevokedInvitationNoLongerWorks() throws Exception {
        var id = idOf(create("{}"));

        mockMvc.perform(delete(INVITATIONS + "/" + id).with(admin())).andExpect(status().isNoContent());

        mockMvc.perform(get(INVITATIONS).with(admin())).andExpect(jsonPath("$", hasSize(0)));
        signUp(id).andExpect(status().isGone());
    }

    @Test
    void revokingAnUnknownInvitationIsNotFound() throws Exception {
        mockMvc.perform(delete(INVITATIONS + "/no-such-invitation").with(admin()))
                .andExpect(status().isNotFound());
    }

    @Test
    void anInvitationWorksOnce() throws Exception {
        var id = idOf(create("{}"));

        signUp(id).andExpect(jsonPath("$.state").value("ACTIVE"));
        signUp(id).andExpect(status().isGone());
        mockMvc.perform(get(INVITATIONS).with(admin())).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void anExpiredInvitationDoesNotWork() throws Exception {
        signUp(expired().getId())
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("INVITATION_INVALID"));
    }

    @Test
    void theNightlyJobDeletesExpiredInvitationsOnly() throws Exception {
        var open = idOf(create("{}"));
        var gone = expired();

        deletionJob.deleteExpiredInvitations();

        assertTrue(invitationRepository.findById(open).isPresent());
        assertTrue(invitationRepository.findById(gone.getId()).isEmpty());
    }

    @Test
    void onlyAnAdministratorCanCreateOne() throws Exception {
        mockMvc.perform(post(INVITATIONS).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    private ResultActions create(String body) throws Exception {
        return mockMvc.perform(post(INVITATIONS).with(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions signUp(String invitation) throws Exception {
        return mockMvc.perform(post("/api/v1/users/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"emailAddress\":\"invited-%s@example.com\",\"password\":\"a-password\",\"invitation\":\"%s\"}"
                        .formatted(UUID.randomUUID(), invitation)));
    }

    private Invitation expired() {
        return invitationRepository.save(Invitation.builder()
                .id(SecretTokens.generate())
                .expiresAt(Instant.now().minus(Duration.ofDays(1)))
                .build());
    }

    private static void assertExpiresIn(Duration validity, String body) {
        var expiresAt = Instant.parse(JsonPath.read(body, "$.expiresAt"));
        var expected = Instant.now().plus(validity);
        assertTrue(Duration.between(expiresAt, expected).abs().compareTo(Duration.ofMinutes(1)) < 0,
                "expires at " + expiresAt + ", expected about " + expected);
    }

    private static String idOf(ResultActions created) throws Exception {
        return JsonPath.read(created.andReturn().getResponse().getContentAsString(), "$.id");
    }

    private static RequestPostProcessor admin() {
        return operator(TestInstance.ADMINISTRATOR);
    }
}
