package com.sterul.opencookbookapiserver.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.jayway.jsonpath.JsonPath;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.repositories.HouseholdMembershipRepository;
import com.sterul.opencookbookapiserver.repositories.HouseholdRepository;
import com.sterul.opencookbookapiserver.repositories.UserRepository;

/** Starting, renaming, joining and leaving a household. The caps are set low rather than reached. */
@SpringBootTest(properties = {
        "opencookbook.households.maxMembers=2",
        "opencookbook.households.maxPerUser=1",
})
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
class HouseholdApiIntegrationTest extends IntegrationTestBase {

    private static final String ANNA = "hh-anna@example.invalid";
    private static final String BERT = "hh-bert@example.invalid";
    private static final String CARLA = "hh-carla@example.invalid";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private HouseholdRepository householdRepository;
    @Autowired
    private HouseholdMembershipRepository membershipRepository;

    @BeforeEach
    void setup() {
        membershipRepository.deleteAll();
        householdRepository.deleteAll();
        userNamed(ANNA);
        userNamed(BERT);
        userNamed(CARLA);
    }

    @Test
    @WithMockUser(username = ANNA)
    void startingOneMakesYouItsFirstMember() throws Exception {
        mockMvc.perform(create("Familie Test", true))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Familie Test"))
                .andExpect(jsonPath("$.memberCount").value(1))
                .andExpect(jsonPath("$.shareRecipes").value(true))
                .andExpect(jsonPath("$.members[0].me").value(true));
    }

    @Test
    @WithMockUser(username = ANNA)
    void anyMemberMayRename() throws Exception {
        var householdId = householdStartedBy(ANNA);

        mockMvc.perform(put("/api/v1/households/" + householdId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"WG Sommer\",\"shareRecipes\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("WG Sommer"));
    }

    @Test
    void somebodyWhoIsNotAMemberIsToldNothing() throws Exception {
        var householdId = householdStartedBy(ANNA);

        // "Not found" rather than "not allowed": a household must not be shown to exist by asking.
        mockMvc.perform(get("/api/v1/households/" + householdId).with(asUser(CARLA)))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/v1/households/" + householdId + "/sharing")
                        .with(asUser(CARLA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shareRecipes\": true}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void aHouseholdFillsUp() throws Exception {
        var householdId = householdStartedBy(ANNA);
        join(householdId, BERT);

        mockMvc.perform(post("/api/v1/household-invites/" + inviteTo(householdId) + "/accept")
                        .with(asUser(CARLA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shareRecipes\": false}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HOUSEHOLD_FULL"));
    }

    @Test
    void oneAccountCanOnlyBeInSoMany() throws Exception {
        householdStartedBy(ANNA);

        mockMvc.perform(create("A second one", false).with(asUser(ANNA)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TOO_MANY_HOUSEHOLDS"));
    }

    /**
     * The household row is written before its creator joins it, and joining is what hits the
     * cap. The refusal must take the household with it, or the server keeps one nobody is in.
     * This held only once ApiException became unchecked, which is what makes Spring roll the
     * transaction back.
     */
    @Test
    void aHouseholdItsCreatorCannotJoinIsNotKept() throws Exception {
        householdStartedBy(ANNA);
        var householdsBefore = householdRepository.count();

        mockMvc.perform(create("A second one", false).with(asUser(ANNA)))
                .andExpect(status().isConflict());

        assertEquals(householdsBefore, householdRepository.count());
    }

    @Test
    void joiningTwiceIsRefused() throws Exception {
        var householdId = householdStartedBy(ANNA);
        join(householdId, BERT);

        mockMvc.perform(post("/api/v1/household-invites/" + inviteTo(householdId) + "/accept")
                        .with(asUser(BERT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shareRecipes\": false}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_A_MEMBER"));
    }

    @Test
    void anyMemberMayRemoveAnyMember() throws Exception {
        var householdId = householdStartedBy(ANNA);
        join(householdId, BERT);
        var annaId = userRepository.findByEmailAddress(ANNA).getUserId();

        // The one who joined throws out the one who started it. Safe because a household owns
        // nothing: Anna keeps every recipe she has.
        mockMvc.perform(delete("/api/v1/households/" + householdId + "/members/" + annaId)
                        .with(asUser(BERT)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/households/" + householdId).with(asUser(BERT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberCount").value(1));
        mockMvc.perform(get("/api/v1/households/" + householdId).with(asUser(ANNA)))
                .andExpect(status().isNotFound());
    }

    @Test
    void theLastOneOutTakesTheHouseholdWithThem() throws Exception {
        var householdId = householdStartedBy(ANNA);
        var annaId = userRepository.findByEmailAddress(ANNA).getUserId();

        mockMvc.perform(delete("/api/v1/households/" + householdId + "/members/" + annaId)
                        .with(asUser(ANNA)))
                .andExpect(status().isNoContent());

        assertEquals(0, householdRepository.count(),
                "A household is nothing but its members, and it owns no recipe to orphan");
    }

    // ------------------------------------------------------------------------------- helpers

    private static RequestPostProcessor asUser(String name) {
        return SecurityMockMvcRequestPostProcessors.user(name);
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder create(
            String name, boolean shareRecipes) {
        return post("/api/v1/households")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + name + "\",\"shareRecipes\":" + shareRecipes + "}");
    }

    private String householdStartedBy(String emailAddress) throws Exception {
        var body = mockMvc.perform(create("Familie Test", true).with(asUser(emailAddress)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private String inviteTo(String householdId) throws Exception {
        var body = mockMvc.perform(post("/api/v1/households/" + householdId + "/invites")
                        .with(asUser(ANNA)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token").toString();
    }

    private void join(String householdId, String emailAddress) throws Exception {
        mockMvc.perform(post("/api/v1/household-invites/" + inviteTo(householdId) + "/accept")
                        .with(asUser(emailAddress))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shareRecipes\": false}"))
                .andExpect(status().isOk());
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
