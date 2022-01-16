package com.sterul.opencookbookapiserver.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;

import com.sterul.opencookbookapiserver.controllers.IngredientsController;
import com.sterul.opencookbookapiserver.controllers.RecipeController;
import com.sterul.opencookbookapiserver.controllers.requests.RecipeRequest;
import com.sterul.opencookbookapiserver.entities.Ingredient;
import com.sterul.opencookbookapiserver.entities.IngredientNeed;
import com.sterul.opencookbookapiserver.entities.account.User;
import com.sterul.opencookbookapiserver.repositories.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
class RecipeAPIIntegrationTest {

    @Autowired
    RecipeController cut;

    @Autowired
    IngredientsController ingredientsController;

    @Autowired
    UserRepository userRepository;

    User testUser;

    @BeforeEach
    void setup() {
        testUser = new User();
        testUser.setEmailAddress("test@test.com");
        userRepository.save(testUser);

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
                Ingredient.builder()
                        .name("TestIngredient")
                        .build());

        var newRecipe = RecipeRequest.builder()
                .title("test")
                .servings(5)
                .preparationSteps(Arrays.asList(new String[] { "Step1", "Step2" }))
                .neededIngredients(Arrays.asList(
                        IngredientNeed.builder()
                                .amount(4F)
                                .ingredient(ingredient)
                                .build()))
                .build();

        var newRecipeResponse = cut.newRecipe(newRecipe);
        assertEquals(newRecipeResponse.getTitle(), newRecipe.getTitle());
        assertEquals(newRecipeResponse.getServings(), newRecipe.getServings());
        assertEquals(newRecipeResponse.getPreparationSteps(), newRecipe.getPreparationSteps());
        assertEquals(newRecipeResponse.getNeededIngredients(),
                newRecipe.getNeededIngredients());
        assertEquals(newRecipeResponse.getRecipeGroups(), newRecipe.getRecipeGroups());
        assertEquals(newRecipeResponse.getImages(), newRecipe.getImages());

    }

}
