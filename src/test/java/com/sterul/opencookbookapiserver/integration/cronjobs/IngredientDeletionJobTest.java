package com.sterul.opencookbookapiserver.integration.cronjobs;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.sterul.opencookbookapiserver.cronjobs.IngredientDeletionJob;
import com.sterul.opencookbookapiserver.entities.Ingredient;
import com.sterul.opencookbookapiserver.entities.IngredientNeed;
import com.sterul.opencookbookapiserver.entities.recipe.Recipe;
import com.sterul.opencookbookapiserver.integration.IntegrationTest;
import com.sterul.opencookbookapiserver.repositories.IngredientRepository;
import com.sterul.opencookbookapiserver.repositories.RecipeRepository;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class IngredientDeletionJobTest extends IntegrationTest {
    @Autowired
    IngredientDeletionJob cut;

    @MockBean
    IngredientRepository ingredientRepository;

    @MockBean
    RecipeRepository recipeRepository;

    @Mock
    Recipe testRecipe;

    @Mock
    Ingredient oldTestIngredient;

    @Mock
    Ingredient newTestIngredient;

    @BeforeEach
    void setup() {
        when(oldTestIngredient.getCreatedOn()).thenReturn(Instant.now().minus(100, ChronoUnit.DAYS));
        when(oldTestIngredient.getId()).thenReturn(1L);
        
        when(newTestIngredient.getCreatedOn()).thenReturn(Instant.now().minus(100, ChronoUnit.DAYS));
        when(newTestIngredient.getId()).thenReturn(2L);

        when(ingredientRepository.findAllByIsPublicIngredientAndCreatedOnBefore(eq(false),any()))
           .thenReturn(new ArrayList<>(Arrays.asList(newTestIngredient, oldTestIngredient)));

        testRecipe = Recipe.builder()
                .id(1L)
                .images(List.of())
                .neededIngredients(List.of(IngredientNeed.builder()
                        .ingredient(newTestIngredient)
                        .build()))
                .build();

        when(recipeRepository.findAll()).thenReturn(List.of(testRecipe));
    }

    @Test
    void onlyUnusedIngredientIsDeleted() {

        cut.deleteUnlinkedIngredients();

        verify(ingredientRepository, times(1)).deleteAll(List.of(oldTestIngredient));
    }
}
