package com.sterul.opencookbookapiserver.integration;

import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.sterul.opencookbookapiserver.entities.Ingredient;
import com.sterul.opencookbookapiserver.entities.RecipeImage;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.entities.recipe.RecipeGroup;
import com.sterul.opencookbookapiserver.repositories.RecipeGroupRepository;
import com.sterul.opencookbookapiserver.repositories.RecipeImageRepository;
import com.sterul.opencookbookapiserver.repositories.UserRepository;
import com.sterul.opencookbookapiserver.services.IngredientService;

/** Every reference in a recipe request, tried with something of another account. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
class RecipeReferenceOwnershipIntegrationTest extends IntegrationTest {

    private static final String AUTHOR = "reference-author@example.com";
    private static final String OTHER = "reference-other@example.com";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private IngredientService ingredientService;
    @Autowired
    private RecipeGroupRepository recipeGroupRepository;
    @Autowired
    private RecipeImageRepository recipeImageRepository;

    private CookpalUser other;

    @BeforeEach
    void createAccounts() {
        user(AUTHOR);
        other = user(OTHER);
    }

    @Test
    @WithMockUser(username = AUTHOR)
    void anIngredientIdOfAnotherAccountIsIgnoredInFavourOfTheName() throws Exception {
        var foreign = ingredientService.createOrGetIngredient(Ingredient.builder().name("Fremdzutat").build(), other);

        mockMvc.perform(post("/api/v1/recipes")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "title": "Borrowed", "servings": 1,
                          "neededIngredients": [ { "amount": 1, "ingredient": { "id": %d, "name": "Eigene Zutat" } } ] }
                        """.formatted(foreign.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.neededIngredients[0].ingredient.name").value("Eigene Zutat"))
                .andExpect(jsonPath("$.neededIngredients[0].ingredient.id").value(not(foreign.getId().intValue())));
    }

    @Test
    @WithMockUser(username = AUTHOR)
    void aFilledInLineWithoutAnIngredientNameIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/recipes")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "title": "Nameless", "servings": 1,
                          "neededIngredients": [ { "amount": 1, "ingredient": { "id": 1 } } ] }
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @WithMockUser(username = AUTHOR)
    void anEntirelyEmptyLineIsLeftOut() throws Exception {
        mockMvc.perform(post("/api/v1/recipes")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "title": "Untouched editor line", "servings": 1,
                          "neededIngredients": [
                            { "amount": null, "unit": "", "ingredient": { "name": "" } },
                            { "amount": 1, "unit": "EL", "ingredient": { "name": "Öl" } }
                          ] }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.neededIngredients.length()").value(1))
                .andExpect(jsonPath("$.neededIngredients[0].ingredient.name").value("Öl"));
    }

    @Test
    @WithMockUser(username = AUTHOR)
    void aRecipeGroupOfAnotherAccountIsNotFound() throws Exception {
        var foreignGroup = recipeGroupRepository.save(RecipeGroup.builder().title("Theirs").owner(other).build());

        mockMvc.perform(post("/api/v1/recipes")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "title": "Filed elsewhere", "servings": 1, "recipeGroups": [ { "id": %d } ] }
                        """.formatted(foreignGroup.getId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @WithMockUser(username = AUTHOR)
    void anImageOfAnotherAccountIsNotFound() throws Exception {
        var foreignImage = recipeImageRepository.save(RecipeImage.builder().owner(other).build());

        mockMvc.perform(post("/api/v1/recipes")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "title": "Borrowed picture", "servings": 1, "images": [ { "uuid": "%s" } ] }
                        """.formatted(foreignImage.getUuid())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @WithMockUser(username = AUTHOR)
    void aRecipeResponseDoesNotExposeTheIngredientOwner() throws Exception {
        mockMvc.perform(post("/api/v1/recipes")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "title": "Private", "servings": 1,
                          "neededIngredients": [ { "amount": 1, "ingredient": { "name": "Mehl" } } ] }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.neededIngredients[0].ingredient.owner").doesNotExist())
                .andExpect(jsonPath("$.neededIngredients[0].createdOn").doesNotExist());
    }

    @Test
    @WithMockUser(username = AUTHOR)
    void aPrivateIngredientOfAnotherAccountIsNotFound() throws Exception {
        var foreign = ingredientService.createOrGetIngredient(Ingredient.builder().name("Geheimzutat").build(), other);

        mockMvc.perform(get("/api/v1/ingredients/{id}", foreign.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    private CookpalUser user(String emailAddress) {
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
