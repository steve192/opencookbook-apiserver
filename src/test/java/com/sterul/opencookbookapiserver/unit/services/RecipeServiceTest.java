package com.sterul.opencookbookapiserver.unit.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import com.sterul.opencookbookapiserver.entities.Ingredient;
import com.sterul.opencookbookapiserver.entities.IngredientNeed;
import com.sterul.opencookbookapiserver.entities.RecipeImage;
import com.sterul.opencookbookapiserver.entities.WeekplanDay;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.entities.recipe.Recipe;
import com.sterul.opencookbookapiserver.entities.recipe.RecipeGroup;
import com.sterul.opencookbookapiserver.repositories.RecipeRepository;
import com.sterul.opencookbookapiserver.services.IngredientService;
import com.sterul.opencookbookapiserver.services.RecipeGroupService;
import com.sterul.opencookbookapiserver.services.RecipeImageService;
import com.sterul.opencookbookapiserver.services.RecipeService;
import com.sterul.opencookbookapiserver.services.WeekplanService;

@SpringBootTest
@ActiveProfiles("unit-test")
class RecipeServiceTest {

    @Autowired
    private RecipeService cut;

    @MockBean
    private RecipeRepository recipeRepository;
    @MockBean
    private RecipeImageService recipeImageService;
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
    private CookpalUser testUser;

    @Captor
    private ArgumentCaptor<Recipe> recipeCaptor;
    private List<Recipe> testRecipeList = new ArrayList<>();

    private String testRecipeImageUUID = "duniwqndiu2u912nd9";

    @BeforeEach
    void setup() {
        when(mockRecipe.getOwner()).thenReturn(testUser);
    }

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
    void recipeDeleted() throws IOException {
        whenRecipeExists("test", 1L, Arrays.asList(Recipe.RecipeType.MEAT));
        cut.deleteRecipe(1L);

        verify(recipeRepository, times(1)).deleteById(1L);
        verify(recipeImageService, times(1)).deleteImage(testRecipeImageUUID);
    }

    @Test
    void recipeDeletionTriggersWeekplanChange() {
        when(weekplanService.getWeekplanDaysByRecipe(1L)).thenReturn(Arrays.asList(mockWeekplanDay));
        whenRecipeExists("Test", Long.valueOf(1), Arrays.asList( Recipe.RecipeType.MEAT));

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
        var excpectedRecipe = whenRecipeExists("Poké-Bowl mit Räucherlachs und Gemüse", null,
                Arrays.asList(Recipe.RecipeType.MEAT));
        whenRecipeExists("Gebackene Laugen-Käse-Knödel", null, Arrays.asList(Recipe.RecipeType.MEAT));
        whenRecipeExists("Räucherlachs Aprikosen-Curry Sauce", null, Arrays.asList(Recipe.RecipeType.MEAT));

        var results = cut.searchUserRecipes(testUser, "Gemüs", Arrays.asList(Recipe.RecipeType.MEAT));
        assertEquals(excpectedRecipe, results.get(0));
    }

    @Test
    void fuzzySearchFindsNoResults() {
        whenRecipeExists("Poké-Bowl mit Räucherlachs und Gemüse", null, Arrays.asList(Recipe.RecipeType.MEAT));
        whenRecipeExists("Gebackene Laugen-Käse-Knödel", null, Arrays.asList(Recipe.RecipeType.MEAT));
        whenRecipeExists("Räucherlachs Aprikosen-Curry Sauce", null, Arrays.asList(Recipe.RecipeType.MEAT));

        var results = cut.searchUserRecipes(testUser, "Tomats", Arrays.asList(Recipe.RecipeType.MEAT));
        assertTrue(results.isEmpty());
    }

    private Recipe whenRecipeExists(String testRecipe, Long id, List<Recipe.RecipeType> types) {
        var recipeId = id == null ? new Random().nextLong() : id;
        var recipe = Recipe.builder()
                .title(testRecipe)
                .id(recipeId)
                .images(List.of(RecipeImage.builder()
                        .uuid(testRecipeImageUUID)
                        .build()))
                .build();
        testRecipeList.add(recipe);
        when(recipeRepository.findByOwnerAndRecipeTypeIn(testUser, types))
                .thenReturn(testRecipeList);
        when(recipeRepository.getById(recipeId)).thenReturn(recipe);

        return recipe;
    }

}
