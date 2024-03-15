package com.sterul.opencookbookapiserver.integration;

import static com.sterul.opencookbookapiserver.integration.TestUtils.whenAuthenticated;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.sterul.opencookbookapiserver.controllers.BringExportController;
import com.sterul.opencookbookapiserver.controllers.BringExportController.ExportCreationRequest;
import com.sterul.opencookbookapiserver.entities.Ingredient;
import com.sterul.opencookbookapiserver.entities.IngredientNeed;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.entities.recipe.Recipe;
import com.sterul.opencookbookapiserver.repositories.RecipeRepository;
import com.sterul.opencookbookapiserver.repositories.UserRepository;
import com.sterul.opencookbookapiserver.services.exceptions.ElementNotFound;

@SpringBootTest
@ActiveProfiles("integration-test")
@Testcontainers
class BringExportAPITest extends IntegrationTest {
        @Autowired
        private BringExportController cut;

        @MockBean
        RecipeRepository recipeRepository;

        @Autowired
        UserRepository userRepository;

        @Test
        @Transactional
        void testBringExportCreation() throws ElementNotFound {
                var testRecipe = Recipe.builder().servings(10)
                                .owner(new CookpalUser(1l, "test@test.com", "dskid", true, null))
                                .neededIngredients(Arrays.asList(
                                                IngredientNeed.builder().amount(10f).unit("Pcs")
                                                                .ingredient(Ingredient.builder().name("Apple").build())
                                                                .build(),
                                                IngredientNeed.builder().amount(500f).unit("g")
                                                                .ingredient(Ingredient.builder().name("Banana").build())
                                                                .build()))
                                .build();

                whenAuthenticated(userRepository);
                when(recipeRepository.findById(any())).thenReturn(Optional.of(testRecipe));

                var result = cut.createBringExport(new ExportCreationRequest(123456789l));

                assertEquals( HttpStatus.OK, result.getStatusCode());

                var result2 = cut.getExportData(result.getBody().exportId());
                assertEquals(HttpStatus.OK, result2.getStatusCode());

                var expectedResult = """
                                <div itemType='http://schema.org/Recipe'><span itemProp='yield'>10</span><h1 itemProp='name'>Cookpal Import</h1><img itemprop="image" src="favicon.ico"/><ul><li itemProp='ingredients'>10 Pcs Apple</li><li itemProp='ingredients'>500 g Banana</li></ul></div>""";

                assertEquals(expectedResult, result2.getBody());
        }

        @Test
        void cannotGenerateExportForOtherUser() throws ElementNotFound {
                var testRecipe = Recipe.builder()
                                .owner(new CookpalUser(123l, "tester_other@test.invalid", "dpsadjopsad", true, null))
                                .servings(10)
                                .neededIngredients(Arrays.asList(
                                                IngredientNeed.builder().amount(10f).unit("Pcs")
                                                                .ingredient(Ingredient.builder().name("Apple").build())
                                                                .build(),
                                                IngredientNeed.builder().amount(500f).unit("g")
                                                                .ingredient(Ingredient.builder().name("Banana").build())
                                                                .build()))
                                .build();
                whenAuthenticated(userRepository);
                when(recipeRepository.findById(any())).thenReturn(Optional.of(testRecipe));

                var result = cut.createBringExport(new ExportCreationRequest(123456789l));

                assertEquals( HttpStatus.UNAUTHORIZED, result.getStatusCode());
        }
}
