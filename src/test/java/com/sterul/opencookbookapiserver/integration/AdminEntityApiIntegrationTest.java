package com.sterul.opencookbookapiserver.integration;

import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.jayway.jsonpath.JsonPath;
import com.sterul.opencookbookapiserver.entities.BringExport;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.entities.account.Role;
import com.sterul.opencookbookapiserver.entities.recipe.Recipe;
import com.sterul.opencookbookapiserver.repositories.BringExportRepository;
import com.sterul.opencookbookapiserver.repositories.RecipeRepository;
import com.sterul.opencookbookapiserver.repositories.UserRepository;
import com.sterul.opencookbookapiserver.services.EmailService;

/**
 * What an operator can see and change about the accounts, recipes and exports on the instance.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
class AdminEntityApiIntegrationTest extends IntegrationTest {

    private static final String OWNER = "admin-entities-owner@example.com";
    private static final String OPERATOR = "admin-entities-operator@example.com";
    private static final String ORDINARY_USER = "admin-entities-ordinary@example.com";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RecipeRepository recipeRepository;
    @Autowired
    private BringExportRepository bringExportRepository;

    @MockitoBean
    private EmailService emailService;

    private CookpalUser owner;
    private Recipe recipe;

    @BeforeEach
    void setup() {
        bringExportRepository.deleteAll();
        recipeRepository.deleteAll();

        // The last-administrator guard counts what is in the shared database.
        var everybody = userRepository.findAll();
        everybody.forEach(user -> {
            user.setRoles(null);
            user.setActivated(true);
        });
        userRepository.saveAll(everybody);

        owner = userNamed(OWNER);

        recipe = new Recipe();
        recipe.setTitle("Grandmother's lasagne");
        recipe.setOwner(owner);
        recipe.setServings(4);
        recipe.setPreparationSteps(List.of("Boil", "Bake"));
        recipe = recipeRepository.save(recipe);
    }

    @Test
    void theUserListSaysWhoOwnsHowMuch() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users").with(operator()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.emailAddress=='" + OWNER + "')].recipeCount").value(1))
                .andExpect(jsonPath("$[?(@.emailAddress=='" + OWNER + "')].activated").value(true));
    }

    @Test
    void anOperatorCanCorrectAnAccount() throws Exception {
        mockMvc.perform(put("/api/v1/admin/users/" + owner.getUserId()).with(operator())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"emailAddress":"corrected@example.com","activated":false,"role":"ADMIN"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailAddress").value("corrected@example.com"))
                .andExpect(jsonPath("$.activated").value(false))
                .andExpect(jsonPath("$.roles").value(Role.ADMIN.name()));
    }

    @Test
    void anAddressAlreadyInUseIsRefused() throws Exception {
        userNamed(ORDINARY_USER);

        mockMvc.perform(put("/api/v1/admin/users/" + owner.getUserId()).with(operator())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"emailAddress\":\"" + ORDINARY_USER + "\",\"activated\":true}"))
                .andExpect(status().isConflict());
    }

    @Test
    void anUpdateThatDoesNotSayWhetherTheAccountIsActivatedIsRefused() throws Exception {
        // A field left out would otherwise lock somebody out rather than leave them be.
        mockMvc.perform(put("/api/v1/admin/users/" + owner.getUserId()).with(operator())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"emailAddress\":\"" + OWNER + "\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/admin/users/" + owner.getUserId()).with(operator()))
                .andExpect(jsonPath("$.activated").value(true));
    }

    @Test
    void anAccountCanBeLockedWithoutDeletingIt() throws Exception {
        mockMvc.perform(post("/api/v1/admin/users/" + owner.getUserId() + "/deactivate").with(operator()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/admin/users/" + owner.getUserId()).with(operator()))
                .andExpect(jsonPath("$.activated").value(false));
    }

    @Test
    void theRecipeListSaysWhoOwnsEachRecipe() throws Exception {
        mockMvc.perform(get("/api/v1/admin/recipes").with(operator()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Grandmother's lasagne"))
                .andExpect(jsonPath("$[0].ownerEmailAddress").value(OWNER))
                .andExpect(jsonPath("$[0].stepCount").value(2));
    }

    @Test
    void anOperatorCanCorrectARecipe() throws Exception {
        mockMvc.perform(put("/api/v1/admin/recipes/" + recipe.getId()).with(operator())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"title":"Lasagne","servings":6,"totalTime":90,"recipeType":"VEGETARIAN",
                         "preparationSteps":["Boil","Bake","Rest"]}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Lasagne"))
                .andExpect(jsonPath("$.servings").value(6))
                .andExpect(jsonPath("$.totalTime").value(90))
                .andExpect(jsonPath("$.stepCount").value(3))
                .andExpect(jsonPath("$.ownerEmailAddress").value(OWNER));
    }

    @Test
    void aDetailLeftOutOfACorrectionIsCleared() throws Exception {
        // Otherwise a field could never be emptied again.
        mockMvc.perform(put("/api/v1/admin/recipes/" + recipe.getId()).with(operator())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Lasagne\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stepCount").value(0))
                .andExpect(jsonPath("$.totalTime").doesNotExist())
                .andExpect(jsonPath("$.recipeType").doesNotExist());
    }

    @Test
    void aRecipeWithoutATitleIsRefused() throws Exception {
        mockMvc.perform(put("/api/v1/admin/recipes/" + recipe.getId()).with(operator())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void anOperatorCanDeleteAnyRecipe() throws Exception {
        mockMvc.perform(delete("/api/v1/admin/recipes/" + recipe.getId()).with(operator()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/admin/recipes/" + recipe.getId()).with(operator()))
                .andExpect(status().isNotFound());
    }

    @Test
    void bringExportsSayWhoExportedThemAndCanBeTakenAway() throws Exception {
        var export = bringExportRepository.save(BringExport.builder()
                .owner(owner)
                .baseAmount(4)
                .ingredients(List.of("500 g Flour"))
                .build());

        mockMvc.perform(get("/api/v1/admin/bringexports").with(operator()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ownerEmailAddress").value(OWNER))
                .andExpect(jsonPath("$[0].ingredientCount").value(1));

        mockMvc.perform(delete("/api/v1/admin/bringexports/" + export.getId()).with(operator()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/admin/bringexports").with(operator()))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void theLastAdministratorCannotBeTakenAway() throws Exception {
        var onlyOperator = userNamed(OPERATOR);
        onlyOperator.setRoles(Role.ADMIN);
        userRepository.save(onlyOperator);

        // Each of these would leave nobody able to open this panel.
        mockMvc.perform(put("/api/v1/admin/users/" + onlyOperator.getUserId()).with(operator())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"emailAddress\":\"" + OPERATOR + "\",\"activated\":true}"))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/v1/admin/users/" + onlyOperator.getUserId() + "/deactivate")
                .with(operator()))
                .andExpect(status().isConflict());

        mockMvc.perform(delete("/api/v1/admin/users/" + onlyOperator.getUserId()).with(operator()))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/v1/admin/users/" + onlyOperator.getUserId()).with(operator()))
                .andExpect(jsonPath("$.roles").value(Role.ADMIN.name()))
                .andExpect(jsonPath("$.activated").value(true));
    }

    @Test
    void anAdministratorCanStandDownWhileAnotherOneRemains() throws Exception {
        var stepping = userNamed(OPERATOR);
        stepping.setRoles(Role.ADMIN);
        userRepository.save(stepping);
        var staying = userNamed(ORDINARY_USER);
        staying.setRoles(Role.ADMIN);
        userRepository.save(staying);

        mockMvc.perform(put("/api/v1/admin/users/" + stepping.getUserId()).with(operator())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"emailAddress\":\"" + OPERATOR + "\",\"activated\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles").doesNotExist());
    }

    @Test
    void anOperatorCanAddCorrectAndDeleteAPublicIngredient() throws Exception {
        var created = mockMvc.perform(post("/api/v1/admin/ingredients").with(operator())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"Potato","additionalInfo":"floury","nutrientsEnergy":77,
                         "alternativeNames":[{"languageIsoCode":"de","alternativeName":"Kartoffel"}]}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Potato"))
                // Public and ownerless, whatever the request said.
                .andExpect(jsonPath("$.publicIngredient").value(true))
                .andExpect(jsonPath("$.ownerUserId").doesNotExist())
                .andExpect(jsonPath("$.alternativeNames[0].alternativeName").value("Kartoffel"))
                .andReturn();
        var id = JsonPath.read(created.getResponse().getContentAsString(), "$.id").toString();

        mockMvc.perform(put("/api/v1/admin/ingredients/" + id).with(operator())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"id":9999,"name":"Potatoes","nutrientsEnergy":80,"alternativeNames":[]}
                        """))
                .andExpect(status().isOk())
                // The path decides, not the body.
                .andExpect(jsonPath("$.id").value(Integer.parseInt(id)))
                .andExpect(jsonPath("$.name").value("Potatoes"))
                .andExpect(jsonPath("$.publicIngredient").value(true))
                .andExpect(jsonPath("$.alternativeNames.length()").value(0));

        mockMvc.perform(delete("/api/v1/admin/ingredients/" + id).with(operator()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/admin/ingredients/" + id).with(operator()))
                .andExpect(status().isNotFound());
    }

    @Test
    void anAlternativeNameIdFromAnotherIngredientBecomesANewNameInsteadOfOverwritingIt()
            throws Exception {
        var borrowedFrom = createPublicIngredient("Carrot", "Karotte");
        var borrowedNameId = JsonPath.read(borrowedFrom, "$.alternativeNames[0].id").toString();
        var target = JsonPath.read(createPublicIngredient("Parsnip", "Pastinake"), "$.id")
                .toString();

        mockMvc.perform(put("/api/v1/admin/ingredients/" + target).with(operator())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"Parsnip","alternativeNames":[
                          {"id":%s,"languageIsoCode":"de","alternativeName":"Stolen"}]}
                        """.formatted(borrowedNameId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alternativeNames[0].alternativeName").value("Stolen"))
                .andExpect(jsonPath("$.alternativeNames[0].id")
                        .value(not(Integer.parseInt(borrowedNameId))));

        // The other ingredient still says what it said.
        mockMvc.perform(get("/api/v1/admin/ingredients/"
                + JsonPath.read(borrowedFrom, "$.id").toString()).with(operator()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alternativeNames[0].alternativeName").value("Karotte"));
    }

    private String createPublicIngredient(String name, String alternativeName) throws Exception {
        return mockMvc.perform(post("/api/v1/admin/ingredients").with(operator())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"%s","alternativeNames":[
                          {"languageIsoCode":"de","alternativeName":"%s"}]}
                        """.formatted(name, alternativeName)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void anIngredientWithoutANameIsRefused() throws Exception {
        mockMvc.perform(post("/api/v1/admin/ingredients").with(operator())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void anOrdinaryUserCannotChangeSomebodyElsesAccount() throws Exception {
        mockMvc.perform(put("/api/v1/admin/users/" + owner.getUserId()).with(user(ORDINARY_USER))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"emailAddress\":\"hijacked@example.com\",\"activated\":true}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void anOrdinaryUserCannotDeleteSomebodyElsesRecipe() throws Exception {
        mockMvc.perform(delete("/api/v1/admin/recipes/" + recipe.getId()).with(user(ORDINARY_USER)))
                .andExpect(status().isForbidden());
    }

    private static RequestPostProcessor operator() {
        return user(OPERATOR).authorities(new SimpleGrantedAuthority("ADMIN"));
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
