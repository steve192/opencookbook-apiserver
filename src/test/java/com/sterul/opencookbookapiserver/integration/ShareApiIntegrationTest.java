package com.sterul.opencookbookapiserver.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.FileInputStream;
import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;
import com.sterul.opencookbookapiserver.entities.Ingredient;
import com.sterul.opencookbookapiserver.entities.IngredientNeed;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.entities.recipe.Recipe;
import com.sterul.opencookbookapiserver.repositories.RecipeRepository;
import com.sterul.opencookbookapiserver.repositories.ShareRepository;
import com.sterul.opencookbookapiserver.repositories.UserRepository;
import com.sterul.opencookbookapiserver.services.IllegalFiletypeException;
import com.sterul.opencookbookapiserver.services.RecipeImageService;
import com.sterul.opencookbookapiserver.services.RecipeService;

/**
 * The behaviour of a share link end to end.
 *
 * The assertions worth reading twice are the ones about what is <em>not</em> there: that reading a
 * share needs no token, that everything else under the sharing prefixes still does, that a link
 * carries no trace of who owns the recipe, and that one share is not a way into another recipe's
 * images.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
class ShareApiIntegrationTest extends IntegrationTest {

    private static final String OWNER = "share-owner@example.com";
    private static final String IMPORTER = "share-importer@example.com";
    private static final String STRANGER = "share-stranger@example.com";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RecipeRepository recipeRepository;
    @Autowired
    private RecipeService recipeService;
    @Autowired
    private RecipeImageService recipeImageService;
    @Autowired
    private ShareRepository shareRepository;
    @Autowired
    private ResourceLoader resourceLoader;

    private CookpalUser owner;
    private Long recipeId;
    private String imageUuid;
    private Long unsharedRecipeId;
    private String unsharedImageUuid;

    @BeforeEach
    void setup() throws IOException {
        shareRepository.deleteAll();
        owner = userNamed(OWNER);
        userNamed(IMPORTER);
        userNamed(STRANGER);

        imageUuid = storedImageOf(owner);
        recipeId = recipeWithImage("Shared lasagne", owner, imageUuid);

        unsharedImageUuid = storedImageOf(owner);
        unsharedRecipeId = recipeWithImage("Private lasagne", owner, unsharedImageUuid);
    }

    // ---------------------------------------------------------------- creating and revoking

    @Test
    @WithMockUser(username = OWNER)
    void sharingTwiceHandsOutTheSameLink() throws Exception {
        var firstShareId = shareRecipe(recipeId);
        var secondShareId = shareRecipe(recipeId);

        assertEquals(firstShareId, secondShareId,
                "A second link would be public, and its owner would never see it to revoke it");
        assertEquals(1, shareRepository.count());
    }

    @Test
    @WithMockUser(username = OWNER)
    void aNewShareCarriesItsExpiryAndItsAddress() throws Exception {
        mockMvc.perform(shareRequestFor(recipeId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shareId").isNotEmpty())
                .andExpect(jsonPath("$.recipeId").value(recipeId))
                .andExpect(jsonPath("$.expiresAt").isNotEmpty())
                .andExpect(jsonPath("$.accessCount").value(0))
                .andExpect(jsonPath("$.shareUrl").value(Matchers.containsString("/share/")));
    }

    @Test
    @WithMockUser(username = OWNER)
    void aRecipeThatIsNotSharedHasNoShares() throws Exception {
        mockMvc.perform(get("/api/v1/shares").param("recipeId", unsharedRecipeId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @WithMockUser(username = STRANGER)
    void somebodyElsesRecipeCannotBeShared() throws Exception {
        mockMvc.perform(shareRequestFor(recipeId))
                .andExpect(status().isForbidden());
    }

    @Test
    void revokingStopsTheLinkFromResolving() throws Exception {
        var shareId = shareRecipeAs(OWNER, recipeId);

        readShare(shareId).andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/shares/" + shareId).with(user(OWNER)))
                .andExpect(status().isNoContent());

        readShare(shareId).andExpect(status().isNotFound());
    }

    @Test
    void somebodyElsesShareCannotBeRevokedAndIsReportedAsMissing() throws Exception {
        var shareId = shareRecipeAs(OWNER, recipeId);

        mockMvc.perform(delete("/api/v1/shares/" + shareId).with(user(STRANGER)))
                .andExpect(status().isNotFound());

        readShare(shareId).andExpect(status().isOk());
    }

    // ---------------------------------------------------------------- reading a share

    @Test
    void aSharedRecipeIsReadableWithoutAnyAuthentication() throws Exception {
        var shareId = shareRecipeAs(OWNER, recipeId);

        readShare(shareId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Shared lasagne"))
                .andExpect(jsonPath("$.neededIngredients[0].ingredient.name").value("Tomato"))
                .andExpect(jsonPath("$.neededIngredients[0].amount").value(2.0))
                .andExpect(jsonPath("$.preparationSteps[0]").value("Chop the tomato"))
                .andExpect(jsonPath("$.images[0].uuid").value(imageUuid))
                .andExpect(jsonPath("$.servings").value(4));
    }

    @Test
    void aSharedRecipeNeverCarriesAnythingAboutItsOwner() throws Exception {
        var shareId = shareRecipeAs(OWNER, recipeId);

        var body = readShare(shareId).andReturn().getResponse().getContentAsString();

        assertFalse(body.contains(OWNER), "A public URL must not publish the owner's email address");
        assertFalse(body.contains("owner"), "The public response must carry no owner field at all");
    }

    @Test
    void anUnknownShareIsNotFound() throws Exception {
        readShare("00000000-0000-0000-0000-000000000000").andExpect(status().isNotFound());
    }

    @Test
    void aLapsedShareIsIndistinguishableFromAnUnknownOne() throws Exception {
        var shareId = shareRecipeAs(OWNER, recipeId);
        expire(shareId);

        readShare(shareId).andExpect(status().isNotFound());
    }

    @Test
    void readingAShareCountsTowardsItsAccessCount() throws Exception {
        var shareId = shareRecipeAs(OWNER, recipeId);

        readShare(shareId).andExpect(status().isOk());
        readShare(shareId).andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/shares").param("recipeId", recipeId.toString()).with(user(OWNER)))
                .andExpect(jsonPath("$[0].accessCount").value(2));
    }

    @Test
    @WithMockUser(username = OWNER)
    void sharingAgainAfterExpiryHandsOutADifferentLink() throws Exception {
        var lapsedShareId = shareRecipe(recipeId);
        expire(lapsedShareId);

        var renewedShareId = shareRecipe(recipeId);

        assertNotEquals(lapsedShareId, renewedShareId,
                "Reusing the id would bring the old link back to life for everyone it was sent to");
        readShare(lapsedShareId).andExpect(status().isNotFound());
        readShare(renewedShareId).andExpect(status().isOk());
    }

    // ---------------------------------------------------------------- images

    @Test
    void theImagesOfASharedRecipeAreReadableWithoutAuthentication() throws Exception {
        var shareId = shareRecipeAs(OWNER, recipeId);

        mockMvc.perform(get("/api/v1/shared/" + shareId + "/images/" + imageUuid))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", Matchers.containsString("public")));

        mockMvc.perform(get("/api/v1/shared/" + shareId + "/images/thumbnail/" + imageUuid))
                .andExpect(status().isOk());
    }

    @Test
    void aShareIsNotAWayIntoTheImagesOfAnotherRecipe() throws Exception {
        var shareId = shareRecipeAs(OWNER, recipeId);

        mockMvc.perform(get("/api/v1/shared/" + shareId + "/images/" + unsharedImageUuid))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/shared/" + shareId + "/images/thumbnail/" + unsharedImageUuid))
                .andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------- importing

    @Test
    void importingLeavesTheImporterWithARecipeOfTheirOwn() throws Exception {
        var shareId = shareRecipeAs(OWNER, recipeId);

        var response = mockMvc.perform(post("/api/v1/shares/" + shareId + "/import").with(user(IMPORTER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Shared lasagne"))
                .andExpect(jsonPath("$.servings").value(4))
                .andExpect(jsonPath("$.preparationSteps[0]").value("Chop the tomato"))
                .andReturn().getResponse().getContentAsString();

        Long importedRecipeId = ((Number) JsonPath.read(response, "$.id")).longValue();
        assertNotEquals(recipeId, importedRecipeId);

        var importedRecipe = recipeRepository.findById(importedRecipeId).orElseThrow();
        assertEquals(userNamed(IMPORTER).getUserId(), importedRecipe.getOwner().getUserId());

        var importedImageUuid = JsonPath.read(response, "$.images[0].uuid").toString();
        assertNotEquals(imageUuid, importedImageUuid,
                "A referenced image would vanish the moment the sharer deleted their recipe");
        assertTrue(recipeImageService.getImage(importedImageUuid).length > 0);
    }

    @Test
    void importingCarriesTheShareLinkAsTheSourceOfTheCopy() throws Exception {
        var shareId = shareRecipeAs(OWNER, recipeId);

        mockMvc.perform(post("/api/v1/shares/" + shareId + "/import").with(user(IMPORTER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recipeSource").value(Matchers.containsString(shareId)));
    }

    @Test
    void importingDoesNotCarryTheSharersOrganisationAcross() throws Exception {
        var shareId = shareRecipeAs(OWNER, recipeId);

        mockMvc.perform(post("/api/v1/shares/" + shareId + "/import").with(user(IMPORTER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recipeGroups.length()").value(0));
    }

    @Test
    void alapsedShareCannotBeImported() throws Exception {
        var shareId = shareRecipeAs(OWNER, recipeId);
        expire(shareId);

        mockMvc.perform(post("/api/v1/shares/" + shareId + "/import").with(user(IMPORTER)))
                .andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------- lifecycle

    @Test
    void deletingTheRecipeTakesItsSharesWithIt() throws Exception {
        var shareId = shareRecipeAs(OWNER, recipeId);

        recipeService.deleteRecipe(recipeId);

        assertFalse(shareRepository.existsById(shareId),
                "A share pointing at a deleted recipe would resolve to nothing");
        readShare(shareId).andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------- helpers

    private ResultActions readShare(String shareId) throws Exception {
        return mockMvc.perform(get("/api/v1/shared/" + shareId));
    }

    private MockHttpServletRequestBuilder shareRequestFor(Long id) {
        return post("/api/v1/shares")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"recipeId\": " + id + "}");
    }

    private String shareRecipe(Long id) throws Exception {
        var response = mockMvc.perform(shareRequestFor(id))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.shareId");
    }

    private String shareRecipeAs(String emailAddress, Long id) throws Exception {
        var response = mockMvc.perform(shareRequestFor(id).with(user(emailAddress)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.shareId");
    }

    private void expire(String shareId) {
        var share = shareRepository.findById(shareId).orElseThrow();
        share.setExpiresAt(Instant.now().minus(1, ChronoUnit.MINUTES));
        shareRepository.save(share);
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

    private String storedImageOf(CookpalUser user) throws IOException {
        var jpg = resourceLoader.getResource("classpath:testimages/jpg_image").getFile();
        try (var stream = new FileInputStream(jpg)) {
            return recipeImageService.saveNewImage(stream, jpg.length(), user).getUuid();
        } catch (IllegalFiletypeException e) {
            throw new IllegalStateException("The test image is not readable", e);
        }
    }

    private Long recipeWithImage(String title, CookpalUser recipeOwner, String storedImageUuid) {
        var recipe = Recipe.builder()
                .title(title)
                .owner(recipeOwner)
                .servings(4)
                .preparationSteps(new ArrayList<>(List.of("Chop the tomato", "Bake it")))
                .neededIngredients(new ArrayList<>(List.of(IngredientNeed.builder()
                        .amount(2f)
                        .unit("Stück")
                        .ingredient(Ingredient.builder().name("Tomato").build())
                        .build())))
                .images(new ArrayList<>(List.of(recipeImageService.getImagesByUser(recipeOwner).stream()
                        .filter(image -> image.getUuid().equals(storedImageUuid))
                        .findFirst()
                        .orElseThrow())))
                .build();
        return recipeService.createNewRecipe(recipe).getId();
    }
}
