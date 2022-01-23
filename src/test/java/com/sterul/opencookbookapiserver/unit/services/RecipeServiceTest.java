package com.sterul.opencookbookapiserver.unit.services;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;

import com.sterul.opencookbookapiserver.entities.Ingredient;
import com.sterul.opencookbookapiserver.entities.IngredientNeed;
import com.sterul.opencookbookapiserver.entities.recipe.Recipe;
import com.sterul.opencookbookapiserver.entities.recipe.RecipeGroup;
import com.sterul.opencookbookapiserver.repositories.RecipeRepository;
import com.sterul.opencookbookapiserver.services.IngredientService;
import com.sterul.opencookbookapiserver.services.RecipeGroupService;
import com.sterul.opencookbookapiserver.services.RecipeService;

import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class RecipeServiceTest {

    @Autowired
    private RecipeService cut;

    @MockBean
    private RecipeRepository recipeRepository;
    @MockBean
    private RecipeGroupService recipeGroupService;
    @MockBean
    private IngredientService ingredientService;

    @Mock
    private Recipe mockRecipe;
    @Mock
    private RecipeGroup mockRecipeGroupWithoutId;
    @Mock
    private RecipeGroup mockRecipeGroupWithId;
    @Mock
    private Ingredient mockIngredientWithoutId;
    @Mock
    private IngredientNeed mockIngredientNeed;

    @Test
    void recipeCreated() {
        cut.createNewRecipe(mockRecipe);
        verify(recipeRepository, times(1)).save(mockRecipe);
    }

    @Test
    void recipeGroupCreatedIfNotExistent() {
        when(mockRecipeGroupWithoutId.getId()).thenReturn(null);
        when(mockRecipeGroupWithId.getId()).thenReturn(1L);
        when(recipeGroupService.createRecipeGroup(any())).thenReturn(mockRecipeGroupWithId);
        when(mockRecipe.getRecipeGroups()).thenReturn(Arrays.asList(mockRecipeGroupWithoutId));

        cut.createNewRecipe(mockRecipe);

        verify(recipeGroupService, times(1)).createRecipeGroup(mockRecipeGroupWithoutId);
    }

    @Test
    void ingredientCreatedIfNotExistent() {
        when(mockIngredientWithoutId.getId()).thenReturn(null);
        when(mockIngredientNeed.getIngredient()).thenReturn(mockIngredientWithoutId);
        when(mockRecipe.getNeededIngredients()).thenReturn(Arrays.asList(mockIngredientNeed));

        cut.createNewRecipe(mockRecipe);

        verify(ingredientService, times(1)).createOrGetIngredient(mockIngredientWithoutId);
    }

}
