package com.sterul.opencookbookapiserver.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.jayway.jsonpath.JsonPath;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.entities.recipe.Recipe;
import com.sterul.opencookbookapiserver.repositories.HouseholdMembershipRepository;
import com.sterul.opencookbookapiserver.repositories.HouseholdRepository;
import com.sterul.opencookbookapiserver.repositories.RecipeRepository;
import com.sterul.opencookbookapiserver.repositories.UserRepository;
import com.sterul.opencookbookapiserver.services.IllegalFiletypeException;
import com.sterul.opencookbookapiserver.services.RecipeImageService;
import com.sterul.opencookbookapiserver.services.households.HouseholdCookbook;

/**
 * The access rule from both sides: a household grants reading only, sharing off is felt on the next
 * request, and an image not yet attached to a recipe stays its uploader's.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
class HouseholdCookbookAccessIntegrationTest extends IntegrationTest {

    private static final String ANNA = "anna@example.invalid";
    private static final String BERT = "bert@example.invalid";
    private static final String STRANGER = "stranger@example.invalid";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RecipeRepository recipeRepository;
    @Autowired
    private RecipeImageService recipeImageService;
    @Autowired
    private HouseholdRepository householdRepository;
    @Autowired
    private HouseholdMembershipRepository membershipRepository;
    @Autowired
    private ResourceLoader resourceLoader;

    private CookpalUser anna;
    private Long annasRecipeId;
    private String annasImageUuid;
    private String householdId;

    @BeforeEach
    void setup() throws Exception {
        membershipRepository.deleteAll();
        householdRepository.deleteAll();
        recipeRepository.deleteAll();

        anna = userNamed(ANNA);
        userNamed(BERT);
        userNamed(STRANGER);

        annasImageUuid = storedImageOf(anna);
        annasRecipeId = recipeOf(anna, "Annas Lasagne", annasImageUuid);

        householdId = householdCreatedBy(ANNA);
        joinHousehold(householdId, BERT);
    }

    // ------------------------------------------------------------------- reading across a household

    @Test
    @WithMockUser(username = BERT)
    void aMemberReadsTheCookbookOfAMemberWhoShares() throws Exception {
        mockMvc.perform(get("/api/v1/recipes/" + annasRecipeId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Annas Lasagne"));

        mockMvc.perform(get("/api/v1/recipes-images/" + annasImageUuid))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = STRANGER)
    void somebodyOutsideTheHouseholdReadsNothing() throws Exception {
        mockMvc.perform(get("/api/v1/recipes/" + annasRecipeId))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/recipes-images/" + annasImageUuid))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = BERT)
    void theHouseholdCookbookListsWhatItsSharingMembersOwn() throws Exception {
        mockMvc.perform(get("/api/v1/households/" + householdId + "/recipes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recipes.length()").value(1))
                .andExpect(jsonPath("$.recipes[0].title").value("Annas Lasagne"))
                .andExpect(jsonPath("$.recipes[0].mine").value(false))
                // A summary, not the whole recipe: what is not in the response cannot leak from it.
                .andExpect(jsonPath("$.recipes[0].neededIngredients").doesNotExist());
    }

    @Test
    @WithMockUser(username = BERT)
    void theCookbookIsPaginated() throws Exception {
        for (var index = 0; index < HouseholdCookbook.PAGE_SIZE; index++) {
            plainRecipeOf(anna, "Extra " + index);
        }

        // Ten members of five hundred recipes is not a response.
        mockMvc.perform(get("/api/v1/households/" + householdId + "/recipes").param("page", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recipes.length()").value(HouseholdCookbook.PAGE_SIZE))
                .andExpect(jsonPath("$.last").value(false))
                .andExpect(jsonPath("$.recipes[0].title").value("Annas Lasagne"));
        mockMvc.perform(get("/api/v1/households/" + householdId + "/recipes").param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recipes.length()").value(1))
                .andExpect(jsonPath("$.last").value(true));
    }

    @Test
    @WithMockUser(username = BERT)
    void theCookbookIsSearchedLikeYourOwn() throws Exception {
        plainRecipeOf(anna, "Kartoffelgratin");
        plainRecipeOf(anna, "Linsensuppe");
        // Bert shares nothing, so a search must not reach his cookbook either.
        plainRecipeOf(userNamed(BERT), "Berts Gratin");

        mockMvc.perform(get("/api/v1/households/" + householdId + "/recipes").param("search", "gratin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recipes.length()").value(1))
                .andExpect(jsonPath("$.recipes[0].title").value("Kartoffelgratin"))
                .andExpect(jsonPath("$.last").value(true));
        mockMvc.perform(get("/api/v1/households/" + householdId + "/recipes").param("search", "lasa"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recipes.length()").value(1))
                .andExpect(jsonPath("$.recipes[0].title").value("Annas Lasagne"));
    }

    @Test
    @WithMockUser(username = BERT)
    void searchResultsArePaginatedToo() throws Exception {
        for (var index = 0; index <= HouseholdCookbook.PAGE_SIZE; index++) {
            plainRecipeOf(anna, "Extra " + index);
        }

        mockMvc.perform(get("/api/v1/households/" + householdId + "/recipes")
                        .param("search", "extra").param("page", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recipes.length()").value(HouseholdCookbook.PAGE_SIZE))
                .andExpect(jsonPath("$.last").value(false));
        mockMvc.perform(get("/api/v1/households/" + householdId + "/recipes")
                        .param("search", "extra").param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recipes.length()").value(1))
                .andExpect(jsonPath("$.last").value(true));
    }

    // ------------------------------------------------------------------- sharing switched off

    @Test
    void stoppingSharingIsFeltOnTheNextRequest() throws Exception {
        mockMvc.perform(get("/api/v1/recipes/" + annasRecipeId).with(asUser(BERT)))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/households/" + householdId + "/sharing")
                        .with(asUser(ANNA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shareRecipes\": false}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/recipes/" + annasRecipeId).with(asUser(BERT)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/households/" + householdId + "/recipes").with(asUser(BERT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recipes.length()").value(0));
    }

    @Test
    void leavingTakesTheCookbookWithIt() throws Exception {
        var annaUserId = userRepository.findByEmailAddress(ANNA).getUserId();

        mockMvc.perform(MockMvcRequestBuilders
                        .delete("/api/v1/households/" + householdId + "/members/" + annaUserId)
                        .with(asUser(ANNA)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/recipes/" + annasRecipeId).with(asUser(BERT)))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------- writing never crosses over

    @Test
    @WithMockUser(username = BERT)
    void aMemberMayNotEditOrDeleteWhatTheyCanRead() throws Exception {
        mockMvc.perform(put("/api/v1/recipes/" + annasRecipeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Berts version\",\"servings\":2,\"preparationSteps\":[],"
                                + "\"neededIngredients\":[],\"images\":[],\"recipeGroups\":[]}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(MockMvcRequestBuilders
                        .delete("/api/v1/recipes/" + annasRecipeId))
                .andExpect(status().isNotFound());

        assertEquals("Annas Lasagne", recipeRepository.findById(annasRecipeId).orElseThrow().getTitle());
    }

    @Test
    @WithMockUser(username = BERT)
    void aMemberMaySaveACopyIntoTheirOwnCookbook() throws Exception {
        mockMvc.perform(post("/api/v1/recipes/" + annasRecipeId + "/import"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Annas Lasagne"));

        var bert = userRepository.findByEmailAddress(BERT);
        assertEquals(1, recipeRepository.findByOwner(bert).size(),
                "The copy is the importer's own, and outlives the original");
    }

    @Test
    @WithMockUser(username = STRANGER)
    void somebodyOutsideTheHouseholdMayNotSaveACopyEither() throws Exception {
        // Copying is guarded by the reading rule and nothing else, so this is the assertion that
        // the generalised endpoint did not widen anything.
        mockMvc.perform(post("/api/v1/recipes/" + annasRecipeId + "/import"))
                .andExpect(status().isNotFound());

        var stranger = userRepository.findByEmailAddress(STRANGER);
        assertEquals(0, recipeRepository.findByOwner(stranger).size());
    }

    // ------------------------------------------------------------------- the image trap

    @Test
    void anUploadedImageWithNoRecipeStaysItsUploadersAlone() throws Exception {
        var loose = storedImageOf(anna);

        mockMvc.perform(get("/api/v1/recipes-images/" + loose).with(asUser(ANNA)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/recipes-images/" + loose).with(asUser(BERT)))
                .andExpect(status().isNotFound());
    }

    /** Reading a picture is not permission to remove it: deleting stays with the owner. */
    @Test
    void aMemberWhoMayReadAnImageMayNotDeleteIt() throws Exception {
        mockMvc.perform(delete("/api/v1/recipes-images/" + annasImageUuid).with(asUser(BERT)))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/recipes-images/" + annasImageUuid).with(asUser(ANNA)))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------------- helpers

    private static RequestPostProcessor asUser(String name) {
        return SecurityMockMvcRequestPostProcessors
                .user(name);
    }

    private String householdCreatedBy(String emailAddress) throws Exception {
        var body = mockMvc.perform(post("/api/v1/households")
                        .with(asUser(emailAddress))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Familie Test\",\"shareRecipes\":true}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private void joinHousehold(String household, String emailAddress) throws Exception {
        var invite = mockMvc.perform(post("/api/v1/households/" + household + "/invites")
                        .with(asUser(ANNA)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        var token = JsonPath.read(invite, "$.token").toString();

        mockMvc.perform(post("/api/v1/household-invites/" + token + "/accept")
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

    private Long recipeOf(CookpalUser owner, String title, String imageUuid) {
        var image = recipeImageService.getImagesByUser(owner).stream()
                .filter(stored -> stored.getUuid().equals(imageUuid))
                .findFirst()
                .orElseThrow();
        var recipe = Recipe.builder()
                .title(title)
                .owner(owner)
                .servings(2)
                .images(new ArrayList<>(List.of(image)))
                .build();
        return recipeRepository.save(recipe).getId();
    }

    private void plainRecipeOf(CookpalUser owner, String title) {
        recipeRepository.save(Recipe.builder().title(title).owner(owner).servings(2).images(new ArrayList<>()).build());
    }

    private String storedImageOf(CookpalUser user) throws IOException {
        var jpg = resourceLoader.getResource("classpath:testimages/jpg_image").getFile();
        try (var stream = new FileInputStream(jpg)) {
            return recipeImageService.saveNewImage(stream, jpg.length(), user).getUuid();
        } catch (IllegalFiletypeException e) {
            throw new IllegalStateException("The test image is not readable", e);
        }
    }
}
