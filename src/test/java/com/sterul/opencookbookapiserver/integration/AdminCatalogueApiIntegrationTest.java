package com.sterul.opencookbookapiserver.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.jayway.jsonpath.JsonPath;
import com.sterul.opencookbookapiserver.entities.Ingredient;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.entities.nutrition.CatalogueFood;
import com.sterul.opencookbookapiserver.entities.nutrition.CatalogueFoodName;
import com.sterul.opencookbookapiserver.entities.nutrition.CatalogueNameRule;
import com.sterul.opencookbookapiserver.entities.nutrition.NutrientValues;
import com.sterul.opencookbookapiserver.repositories.CatalogueFoodRepository;
import com.sterul.opencookbookapiserver.repositories.CatalogueNameRuleRepository;
import com.sterul.opencookbookapiserver.repositories.IngredientRepository;
import com.sterul.opencookbookapiserver.repositories.UserRepository;
import com.sterul.opencookbookapiserver.services.nutrition.catalogue.NutritionDatasetImporter;

/** What an administrator can do with the nutrition catalogue. */
@SpringBootTest(properties = "opencookbook.nutrition.enabled=true")
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
class AdminCatalogueApiIntegrationTest extends IntegrationTestBase {

    private static final String OPERATOR = "catalogue-operator@example.com";
    private static final String COOK = "catalogue-cook@example.com";
    private static final String BREAD = """
            {"names":[{"languageIsoCode":"de","name":"Omas Brot","display":true}],
             "nutrients":{"energyKcal":250,"fat":2,"carbohydrates":45,"protein":8}, "negligible":false,
             "portions":[{"unitKey":"slice","grams":40}]}
            """;

    /** No background import of the shipped dataset. */
    @MockitoBean
    private NutritionDatasetImporter backgroundImporter;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private CatalogueFoodRepository foodRepository;
    @Autowired
    private IngredientRepository ingredientRepository;
    @Autowired
    private CatalogueNameRuleRepository nameRuleRepository;
    @Autowired
    private UserRepository userRepository;

    private CatalogueFood potato;

    @BeforeEach
    void setup() {
        ingredientRepository.deleteAll();
        nameRuleRepository.deleteAll();
        foodRepository.deleteAll();
        potato = foodRepository.save(CatalogueFood.builder()
                .catalogueKey("bls-K110100")
                .origin(CatalogueFood.Origin.DATASET)
                .sourceType(CatalogueFood.SourceType.BLS)
                .sourceCode("K110100")
                .nutrients(NutrientValues.builder().energyKcal(77f).build())
                .names(new ArrayList<>(List.of(CatalogueFoodName.builder().languageIsoCode("de").name("Kartoffel")
                        .display(true).origin(CatalogueFoodName.Origin.DATASET).build())))
                .build());
    }

    @Test
    void anOperatorCanAddCorrectAndDeleteACustomFood() throws Exception {
        var id = createBread();

        mockMvc.perform(put("/api/v1/admin/catalogue/foods/" + id).with(operator())
                .contentType(MediaType.APPLICATION_JSON)
                .content(BREAD.replace("250", "260")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nutrients.energyKcal").value(260))
                .andExpect(jsonPath("$.names[0].name").value("Omas Brot"));

        mockMvc.perform(delete("/api/v1/admin/catalogue/foods/" + id).with(operator()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/admin/catalogue/foods/" + id).with(operator()))
                .andExpect(status().isNotFound());
    }

    @Test
    void aFoodShippedWithTheDatasetIsReadOnly() throws Exception {
        mockMvc.perform(put("/api/v1/admin/catalogue/foods/" + potato.getId()).with(operator())
                .contentType(MediaType.APPLICATION_JSON)
                .content(BREAD))
                .andExpect(status().isConflict());
        mockMvc.perform(delete("/api/v1/admin/catalogue/foods/" + potato.getId()).with(operator()))
                .andExpect(status().isConflict());
    }

    @Test
    void anAdministratorsNameCanBeAddedToADatasetFoodAndRemovedAgain() throws Exception {
        mockMvc.perform(post("/api/v1/admin/catalogue/foods/" + potato.getId() + "/names").with(operator())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"languageIsoCode\":\"de\",\"name\":\" Erdapfel \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.names[?(@.name=='Erdapfel')].origin").value("ADMIN"));

        mockMvc.perform(delete("/api/v1/admin/catalogue/foods/" + potato.getId() + "/names").with(operator())
                .param("languageIsoCode", "de").param("name", "erdapfel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.names.length()").value(1));
    }

    @Test
    void aNameTheDatasetShipsCannotBeRemoved() throws Exception {
        mockMvc.perform(delete("/api/v1/admin/catalogue/foods/" + potato.getId() + "/names").with(operator())
                .param("languageIsoCode", "de").param("name", "Kartoffel"))
                .andExpect(status().isNotFound());
    }

    @Test
    void aNameBelongsToOneFoodOnly() throws Exception {
        var id = createBread();

        mockMvc.perform(post("/api/v1/admin/catalogue/foods/" + id + "/names").with(operator())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"languageIsoCode\":\"de\",\"name\":\"KARTOFFEL\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void aNameTheFoodAlreadyHasIsRefused() throws Exception {
        mockMvc.perform(post("/api/v1/admin/catalogue/foods/" + potato.getId() + "/names").with(operator())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"languageIsoCode\":\"de\",\"name\":\"kartoffel\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void aPortionMustBeInACountUnit() throws Exception {
        mockMvc.perform(post("/api/v1/admin/catalogue/foods").with(operator())
                .contentType(MediaType.APPLICATION_JSON)
                .content(BREAD.replace("\"slice\"", "\"gram\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void negativeNutrientsAreRefused() throws Exception {
        mockMvc.perform(post("/api/v1/admin/catalogue/foods").with(operator())
                .contentType(MediaType.APPLICATION_JSON)
                .content(BREAD.replace("\"fat\":2", "\"fat\":-2")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aCustomFoodIngredientsLinkToIsMergedRatherThanDeleted() throws Exception {
        var id = Long.parseLong(createBread());
        var ingredient = ingredientRepository.save(Ingredient.builder().name("Brot").owner(cook())
                .catalogueFood(foodRepository.findById(id).orElseThrow()).linkSource(Ingredient.LinkSource.ADMIN).build());

        mockMvc.perform(delete("/api/v1/admin/catalogue/foods/" + id).with(operator()))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/v1/admin/catalogue/foods/" + id + "/merge").with(operator())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetId\":" + potato.getId() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.catalogueKey").value("bls-K110100"))
                .andExpect(jsonPath("$.linkedIngredients").value(1))
                .andExpect(jsonPath("$.names[?(@.name=='Omas Brot')].origin").value("ADMIN"));

        mockMvc.perform(get("/api/v1/admin/ingredients/" + ingredient.getId()).with(operator()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.catalogueFoodKey").value("bls-K110100"));
        mockMvc.perform(get("/api/v1/admin/catalogue/foods/" + id).with(operator()))
                .andExpect(status().isNotFound());
    }

    @Test
    void aNameRuleAboutAMergedFoodNowConcernsTheTarget() throws Exception {
        var id = Long.parseLong(createBread());
        nameRuleRepository.save(CatalogueNameRule.builder().name("brotaufstrich").kind(CatalogueNameRule.Kind.NEVER_LINK_TO)
                .catalogueFood(foodRepository.findById(id).orElseThrow()).build());

        mockMvc.perform(post("/api/v1/admin/catalogue/foods/" + id + "/merge").with(operator())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetId\":" + potato.getId() + "}"))
                .andExpect(status().isOk());

        assertEquals(List.of("bls-K110100"), nameRuleRepository.findAllByName("brotaufstrich").stream()
                .map(rule -> rule.getCatalogueFood().getCatalogueKey()).toList());
    }

    @Test
    void theCatalogueListsItsFoodsAndTheShippedDataset() throws Exception {
        mockMvc.perform(get("/api/v1/admin/catalogue/foods").with(operator()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].displayNameDe").value("Kartoffel"));

        mockMvc.perform(get("/api/v1/admin/catalogue/dataset").with(operator()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.label").isNotEmpty())
                .andExpect(jsonPath("$.attributions[?(@.source=='BLS')].license").value("CC BY 4.0"));
    }

    /**
     * Allowed on a dataset food, unlike its names and nutrients: the shipped class is a reading of
     * a description, so an operator who knows the food better outranks it. The mark is what makes
     * the next dataset import leave the correction alone.
     */
    @Test
    void anOperatorCorrectsTheDietOfADatasetFood() throws Exception {
        mockMvc.perform(put("/api/v1/admin/catalogue/foods/" + potato.getId() + "/diet-class").with(operator())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"dietClass\": \"VEGETARIAN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dietClass").value("VEGETARIAN"))
                .andExpect(jsonPath("$.dietClassOrigin").value("ADMIN"));

        mockMvc.perform(put("/api/v1/admin/catalogue/foods/" + potato.getId() + "/diet-class").with(operator())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"dietClass\": null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dietClass").doesNotExist())
                .andExpect(jsonPath("$.dietClassOrigin").doesNotExist());
    }

    @Test
    void anOrdinaryUserCannotTouchTheCatalogue() throws Exception {
        mockMvc.perform(get("/api/v1/admin/catalogue/foods").with(user(COOK)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/admin/catalogue/foods").with(user(COOK))
                .contentType(MediaType.APPLICATION_JSON)
                .content(BREAD))
                .andExpect(status().isForbidden());
    }

    private String createBread() throws Exception {
        var created = mockMvc.perform(post("/api/v1/admin/catalogue/foods").with(operator())
                .contentType(MediaType.APPLICATION_JSON)
                .content(BREAD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.origin").value("CUSTOM"))
                .andExpect(jsonPath("$.readOnly").value(false))
                .andExpect(jsonPath("$.portions[0].origin").value("ADMIN"))
                .andReturn();
        return JsonPath.read(created.getResponse().getContentAsString(), "$.id").toString();
    }

    private CookpalUser cook() {
        var existing = userRepository.findByEmailAddress(COOK);
        if (existing != null) {
            return existing;
        }
        var cook = new CookpalUser();
        cook.setEmailAddress(COOK);
        cook.setActivated(true);
        return userRepository.save(cook);
    }

    private static RequestPostProcessor operator() {
        return user(OPERATOR).authorities(new SimpleGrantedAuthority("ADMIN"));
    }
}
