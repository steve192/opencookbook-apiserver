package com.sterul.opencookbookapiserver.integration;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.entities.recipe.Recipe;
import com.sterul.opencookbookapiserver.entities.sharing.Share;
import com.sterul.opencookbookapiserver.entities.sharing.ShareResourceType;
import com.sterul.opencookbookapiserver.entities.sharing.ShareVisibility;
import com.sterul.opencookbookapiserver.repositories.RecipeRepository;
import com.sterul.opencookbookapiserver.repositories.ShareRepository;
import com.sterul.opencookbookapiserver.repositories.UserRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
class AdminShareApiIntegrationTest extends IntegrationTest {

    private static final String SHARER = "admin-test-sharer@example.com";
    private static final String OPERATOR = "admin-test-operator@example.com";
    private static final String ORDINARY_USER = "admin-test-ordinary@example.com";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RecipeRepository recipeRepository;
    @Autowired
    private ShareRepository shareRepository;

    private String shareId;
    private CookpalUser sharer;
    private Recipe sharedRecipe;

    @BeforeEach
    void setup() {
        shareRepository.deleteAll();
        sharer = userNamed(SHARER);

        sharedRecipe = recipeTitled("A publicly shared lasagne");

        shareId = shareRepository.save(shareOf(sharedRecipe, Duration.ofDays(10), 3)).getId();
    }

    @Test
    void anOperatorSeesWhatIsBeingPublishedAndWhoPublishedIt() throws Exception {
        mockMvc.perform(get("/api/v1/admin/shares").with(operator()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].shareId").value(shareId))
                .andExpect(jsonPath("$[0].recipeTitle").value("A publicly shared lasagne"))
                .andExpect(jsonPath("$[0].ownerEmailAddress").value(SHARER))
                .andExpect(jsonPath("$[0].accessCount").value(3))
                .andExpect(jsonPath("$[0].expired").value(false))
                .andExpect(jsonPath("$[0].shareUrl").value(Matchers.containsString(shareId)));
    }

    @Test
    void theOverviewCountsWhatIsShared() throws Exception {
        // One lapsing inside the "expiring soon" window and one outside it, so the count has to
        // actually discriminate rather than just being the number of shares. A second recipe,
        // because only one public link per recipe may exist.
        shareRepository.save(shareOf(recipeTitled("Something else"), Duration.ofDays(2), 0));

        mockMvc.perform(get("/api/v1/admin/shares/statistics").with(operator()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalShares").value(2))
                .andExpect(jsonPath("$.totalAccesses").value(3))
                .andExpect(jsonPath("$.expiringSoon").value(1));
    }

    @Test
    void anOperatorCanTakeDownSomebodyElsesShare() throws Exception {
        mockMvc.perform(delete("/api/v1/admin/shares/" + shareId).with(operator()))
                .andExpect(status().isNoContent());

        // Taken down means gone, not hidden: the link has to stop working for its recipients.
        mockMvc.perform(get("/api/v1/shared/" + shareId))
                .andExpect(status().isNotFound());
    }

    @Test
    void anOrdinaryUserCannotSeeWhatEverybodyElseIsSharing() throws Exception {
        mockMvc.perform(get("/api/v1/admin/shares").with(user(ORDINARY_USER)))
                .andExpect(status().isForbidden());
    }

    @Test
    void anOrdinaryUserCannotTakeDownSomebodyElsesShare() throws Exception {
        mockMvc.perform(delete("/api/v1/admin/shares/" + shareId).with(user(ORDINARY_USER)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/shared/" + shareId)).andExpect(status().isOk());
    }

    private static RequestPostProcessor operator() {
        return user(OPERATOR).authorities(new SimpleGrantedAuthority("ADMIN"));
    }

    private Share shareOf(Recipe recipe, Duration validity, long accessCount) {
        return Share.builder()
                .owner(sharer)
                .resourceType(ShareResourceType.RECIPE)
                .visibility(ShareVisibility.PUBLIC_LINK)
                .recipe(recipe)
                .expiresAt(Instant.now().plus(validity))
                .accessCount(accessCount)
                .build();
    }

    private Recipe recipeTitled(String title) {
        var recipe = new Recipe();
        recipe.setTitle(title);
        recipe.setOwner(sharer);
        return recipeRepository.save(recipe);
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
