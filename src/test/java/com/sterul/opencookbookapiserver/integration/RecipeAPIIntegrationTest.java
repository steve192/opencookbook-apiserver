package com.sterul.opencookbookapiserver.integration;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.sterul.opencookbookapiserver.controllers.IngredientsController;
import com.sterul.opencookbookapiserver.controllers.RecipeController;
import com.sterul.opencookbookapiserver.controllers.requests.IngredientRequest;
import com.sterul.opencookbookapiserver.controllers.requests.RecipeGroupRequest;
import com.sterul.opencookbookapiserver.controllers.requests.RecipeRequest;
import com.sterul.opencookbookapiserver.entities.Ingredient;
import com.sterul.opencookbookapiserver.entities.IngredientNeed;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.repositories.UserRepository;
import com.sterul.opencookbookapiserver.services.recipeimport.ImportNotSupportedException;
import com.sterul.opencookbookapiserver.services.recipeimport.RecipeImportFailedException;
import com.sterul.opencookbookapiserver.services.recipeimport.recipescrapers.RecipeScraperServiceProxy;

@SpringBootTest
@ActiveProfiles("test")
class RecipeAPIIntegrationTest {

    @Autowired
    private RecipeController cut;

    @Autowired
    private IngredientsController ingredientsController;

    @Autowired
    private UserRepository userRepository;

    private CookpalUser testUser;
    @MockBean
    private RecipeScraperServiceProxy recipeScraperServiceProxy;

    @BeforeEach
    void setupContext() {
        testUser = userRepository.findByEmailAddress("test@test.com");
        if (testUser == null) {
            testUser = new CookpalUser();
            testUser.setUserId(1L);
            testUser.setEmailAddress("test@test.com");
            userRepository.save(testUser);
        }

        // Mock currently logged in user
        Authentication authentication = Mockito.mock(Authentication.class);
        Mockito.when(authentication.getName()).thenReturn("test@test.com");

        SecurityContext securityContext = Mockito.mock(SecurityContext.class);
        Mockito.when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);
    }

    @Test
    @Transactional
    void testRecipeCreation() {
        var ingredient = ingredientsController.create(
                IngredientRequest.builder()
                        .name("TestIngredient")
                        .build());

        var newRecipe = RecipeRequest.builder()
                .title("test")
                .servings(5)
                .preparationSteps(Arrays.asList("Step1", "Step2"))
                .neededIngredients(Arrays.asList(
                        IngredientNeed.builder()
                                .amount(4F)
                                .ingredient(Ingredient.builder()
                                        .id(ingredient.getId())
                                        .build())
                                .build()))
                .build();

        var newRecipeResponse = cut.newRecipe(newRecipe);
        assertEquals(newRecipeResponse.getTitle(), newRecipe.getTitle());
        assertEquals(newRecipeResponse.getServings(), newRecipe.getServings());
        assertListsEqual(newRecipeResponse.getPreparationSteps(), newRecipe.getPreparationSteps());
        assertListsEqual(newRecipeResponse.getNeededIngredients(), newRecipe.getNeededIngredients());

        assertListsEqual(newRecipeResponse.getRecipeGroups(), newRecipe.getRecipeGroups());
        assertListsEqual(newRecipeResponse.getImages(), newRecipe.getImages());

    }

    @Test
    @Transactional
    @DirtiesContext
    void testRecipeCreationWithGroup() {

        var ingredient = ingredientsController.create(
                IngredientRequest.builder()
                        .name("TestIngredient")
                        .build());

        var newRecipe = RecipeRequest.builder()
                .title("test")
                .servings(5)
                .preparationSteps(Arrays.asList("Step1", "Step2"))
                .neededIngredients(Arrays.asList(
                        IngredientNeed.builder()
                                .amount(4F)
                                .ingredient(Ingredient.builder()
                                        .id(ingredient.getId())
                                        .build())
                                .build()))
                .recipeGroups(List.of(RecipeGroupRequest.builder()
                        .title("Test group")
                        .build()))
                .build();

        var newRecipeResponse = cut.newRecipe(newRecipe);
        assertEquals(newRecipeResponse.getTitle(), newRecipe.getTitle());
        assertEquals(newRecipeResponse.getServings(), newRecipe.getServings());
        assertListsEqual(newRecipeResponse.getPreparationSteps(), newRecipe.getPreparationSteps());
        assertListsEqual(newRecipeResponse.getNeededIngredients(), newRecipe.getNeededIngredients());

        assertEquals(newRecipeResponse.getRecipeGroups().get(0).getTitle(),
                newRecipe.getRecipeGroups().get(0).getTitle());
        assertNotNull(newRecipeResponse.getRecipeGroups().get(0).getId());

        assertListsEqual(newRecipeResponse.getImages(), newRecipe.getImages());

    }

    private void whenImportWebsiteNotSupported() {
        try {
            Mockito.when(recipeScraperServiceProxy.scrapeRecipe(Mockito.any()))
                    .thenThrow(new ImportNotSupportedException());
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ImportNotSupportedException e) {
            e.printStackTrace();
        }
    }

    @Test
    void errorWhenRecipeWebsiteImportNotSupported() {
        whenImportWebsiteNotSupported();
        try {
            var response = cut.importRecipe("https://doesnotmatter.com/");
        } catch (ImportNotSupportedException e) {
            return;
        } catch (RecipeImportFailedException e) {
            fail();
        }
        fail();
    }

    @Test
    void availableHostsPassedByRecipeScrapers() throws IOException {
        var testhost = "testhost.com";
        when(recipeScraperServiceProxy.getSupportedHosts()).thenReturn("[\"" + testhost + "\"]");

        var response = cut.getAvilableImportHosts();

        assertTrue(response.contains(testhost));
    }

    private void assertListsEqual(List list1, List list2) {
        assertEquals(new ArrayList(list1),
                new ArrayList(list2));
    }

}
