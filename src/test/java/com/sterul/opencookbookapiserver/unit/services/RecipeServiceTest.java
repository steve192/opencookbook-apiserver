package com.sterul.opencookbookapiserver.unit.services;

import com.sterul.opencookbookapiserver.entities.Ingredient;
import com.sterul.opencookbookapiserver.entities.IngredientNeed;
import com.sterul.opencookbookapiserver.entities.WeekplanDay;
import com.sterul.opencookbookapiserver.entities.account.User;
import com.sterul.opencookbookapiserver.entities.recipe.Recipe;
import com.sterul.opencookbookapiserver.entities.recipe.RecipeGroup;
import com.sterul.opencookbookapiserver.repositories.RecipeRepository;
import com.sterul.opencookbookapiserver.services.IngredientService;
import com.sterul.opencookbookapiserver.services.RecipeGroupService;
import com.sterul.opencookbookapiserver.services.RecipeService;
import com.sterul.opencookbookapiserver.services.WeekplanService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

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
    @MockBean
    private WeekplanService weekplanService;

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
    @Mock
    private WeekplanDay mockWeekplanDay;

    @Mock
    private User testUser;

    @Captor
    private ArgumentCaptor<Recipe> recipeCaptor;
    private List<Recipe> testRecipeList = new ArrayList<>();

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

        verify(ingredientService, times(1)).createOrGetIngredient(eq(mockIngredientWithoutId), any());
    }

    @Test
    void recipeDeleted() {
        cut.deleteRecipe(1L);

        verify(recipeRepository, times(1)).deleteById(1L);
    }

    @Test
    void recipeDeletionTriggersWeekplanChange() {
        when(weekplanService.getWeekplanDaysByRecipe(1L)).thenReturn(Arrays.asList(mockWeekplanDay));

        cut.deleteRecipe(1L);

        verify(weekplanService, times(1)).updateWeekplanDay(mockWeekplanDay);
    }

    @Test
    void servingsCannotBeNegative() {
        mockRecipe.setServings(-10);
        cut.createNewRecipe(mockRecipe);

        verify(recipeRepository).save(recipeCaptor.capture());

        assertTrue(recipeCaptor.getValue().getServings() >= 0);
    }

    @Test
    void recipesAreFuzzySearched() {
        var excpectedRecipe = whenRecipeExists("Poké-Bowl mit Räucherlachs und Gemüse", Arrays.asList(Recipe.RecipeType.MEAT));
        whenRecipeExists("Gebackene Laugen-Käse-Knödel", Arrays.asList(Recipe.RecipeType.MEAT));
        whenRecipeExists("Räucherlachs Aprikosen-Curry Sauce", Arrays.asList(Recipe.RecipeType.MEAT));

        var results = cut.searchUserRecipes(testUser, "Gemüs", Arrays.asList(Recipe.RecipeType.MEAT));
        assertEquals(excpectedRecipe, results.get(0));
    }

    private Recipe whenRecipeExists(String testRecipe, List<Recipe.RecipeType> types) {
        var recipe = Recipe.builder()
                .title(testRecipe)
                .id(new Random().nextLong())
                .build();
        testRecipeList.add(recipe);
        when(recipeRepository.findByOwnerAndRecipeTypeIn(testUser, types))
                .thenReturn(testRecipeList);

        return recipe;
    }


}
