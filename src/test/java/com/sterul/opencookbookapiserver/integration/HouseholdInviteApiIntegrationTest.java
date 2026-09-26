package com.sterul.opencookbookapiserver.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.jayway.jsonpath.JsonPath;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.repositories.HouseholdInviteRepository;
import com.sterul.opencookbookapiserver.repositories.HouseholdMembershipRepository;
import com.sterul.opencookbookapiserver.repositories.HouseholdRepository;
import com.sterul.opencookbookapiserver.repositories.UserRepository;
import com.sterul.opencookbookapiserver.services.households.HouseholdInviteService;

/**
 * Invitation links. Lapsed, revoked and unknown tokens must fail alike, and the preview must show
 * the name and nothing else.
 */
@SpringBootTest(properties = "opencookbook.households.maxLiveInvites=2")
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
class HouseholdInviteApiIntegrationTest extends IntegrationTestBase {

    private static final String ANNA = "inv-anna@example.invalid";
    private static final String BERT = "inv-bert@example.invalid";
    private static final String STRANGER = "inv-stranger@example.invalid";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private HouseholdRepository householdRepository;
    @Autowired
    private HouseholdMembershipRepository membershipRepository;
    @Autowired
    private HouseholdInviteRepository inviteRepository;
    @Autowired
    private HouseholdInviteService inviteService;

    /** So a link can be made to lapse without waiting a fortnight for it. */
    @MockitoBean
    private Clock clock;

    private String householdId;

    @BeforeEach
    void setup() throws Exception {
        atTime(Instant.parse("2026-09-21T10:00:00Z"));
        inviteRepository.deleteAll();
        membershipRepository.deleteAll();
        householdRepository.deleteAll();
        userNamed(ANNA);
        userNamed(BERT);
        userNamed(STRANGER);
        householdId = householdStartedBy(ANNA);
    }

    @Test
    void anInviteCarriesItsLinkAndItsExpiry() throws Exception {
        mockMvc.perform(post("/api/v1/households/" + householdId + "/invites").with(asUser(ANNA)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.link").value(org.hamcrest.Matchers
                        .containsString("/household-invite/")))
                .andExpect(jsonPath("$.expiresAt").isNotEmpty());
    }

    @Test
    void thePreviewSaysTheNameAndNothingElse() throws Exception {
        var token = inviteFrom(ANNA);

        mockMvc.perform(get("/api/v1/household-invites/" + token).with(asUser(STRANGER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.householdName").value("Familie Test"))
                // Neither would be any of a stranger's business, and both would make a leaked
                // link worth walking.
                .andExpect(jsonPath("$.memberCount").doesNotExist())
                .andExpect(jsonPath("$.recipeCount").doesNotExist());
    }

    @Test
    void aLapsedInviteIsIndistinguishableFromOneThatNeverExisted() throws Exception {
        var token = inviteFrom(ANNA);
        atTime(Instant.parse("2026-11-21T10:00:00Z"));

        var lapsed = mockMvc.perform(get("/api/v1/household-invites/" + token).with(asUser(BERT)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INVITE_INVALID"))
                .andReturn().getResponse().getContentAsString();
        var neverExisted = mockMvc.perform(get("/api/v1/household-invites/made-up").with(asUser(BERT)))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();

        assertEquals(JsonPath.read(neverExisted, "$.code").toString(),
                JsonPath.read(lapsed, "$.code").toString(),
                "Told apart, the token becomes something to probe");
    }

    @Test
    void aRevokedInviteStopsWorkingAtOnce() throws Exception {
        var token = inviteFrom(ANNA);

        mockMvc.perform(delete("/api/v1/households/" + householdId + "/invites/" + token)
                        .with(asUser(ANNA)))
                .andExpect(status().isNoContent());

        accept(token, BERT)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INVITE_INVALID"));
    }

    @Test
    void anInviteWorksOnce() throws Exception {
        var token = inviteFrom(ANNA);

        accept(token, BERT)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(householdId))
                .andExpect(jsonPath("$.members.length()").value(2));

        accept(token, STRANGER)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INVITE_INVALID"));
    }

    @Test
    void aRefusedJoinDoesNotUseTheInviteUp() throws Exception {
        var token = inviteFrom(ANNA);

        accept(token, ANNA)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_A_MEMBER"));

        accept(token, BERT).andExpect(status().isOk());
    }

    @Test
    void openInvitesAreCapped() throws Exception {
        inviteFrom(ANNA);
        inviteFrom(ANNA);

        mockMvc.perform(post("/api/v1/households/" + householdId + "/invites").with(asUser(ANNA)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TOO_MANY_INVITES"));
    }

    @Test
    void onlyMembersSeeOrMakeInvites() throws Exception {
        mockMvc.perform(get("/api/v1/households/" + householdId + "/invites").with(asUser(STRANGER)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/households/" + householdId + "/invites").with(asUser(STRANGER)))
                .andExpect(status().isNotFound());
    }

    @Test
    void lapsedInvitesAreSweptUp() throws Exception {
        inviteFrom(ANNA);
        atTime(Instant.parse("2026-11-21T10:00:00Z"));

        var deleted = inviteService.deleteExpiredInvites();

        assertTrue(deleted > 0, "The cronjob is what stops standing grants accumulating");
        assertEquals(0, inviteRepository.count());
    }

    // ------------------------------------------------------------------------------- helpers

    private void atTime(Instant now) {
        when(clock.instant()).thenReturn(now);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        when(clock.withZone(any()))
                .thenReturn(Clock.fixed(now, ZoneOffset.UTC));
    }

    private static RequestPostProcessor asUser(String name) {
        return SecurityMockMvcRequestPostProcessors.user(name);
    }

    private String householdStartedBy(String emailAddress) throws Exception {
        var body = mockMvc.perform(post("/api/v1/households")
                        .with(asUser(emailAddress))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Familie Test\",\"shareRecipes\":true}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private String inviteFrom(String emailAddress) throws Exception {
        var body = mockMvc.perform(post("/api/v1/households/" + householdId + "/invites")
                        .with(asUser(emailAddress)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token").toString();
    }

    private ResultActions accept(String token, String emailAddress) throws Exception {
        return mockMvc.perform(post("/api/v1/household-invites/" + token + "/accept")
                .with(asUser(emailAddress))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"shareRecipes\": false}"));
    }

    private CookpalUser userNamed(String emailAddress) {
        var existing = userRepository.findByEmailAddress(emailAddress);
        if (existing != null) {
            return existing;
        }
        var user = new CookpalUser();
        user.setEmailAddress(emailAddress);
        user.setPasswordHash("irrelevant");
        user.setActivated(true);
        return userRepository.save(user);
    }
}
