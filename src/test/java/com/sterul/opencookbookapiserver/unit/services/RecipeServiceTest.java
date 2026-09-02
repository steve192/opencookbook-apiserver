package com.sterul.opencookbookapiserver.unit.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
import com.sterul.opencookbookapiserver.services.sharing.ShareService;

@ExtendWith(MockitoExtension.class)
class RecipeServiceTest {

    private static final List<Recipe.RecipeType> MEAT_ONLY = List.of(Recipe.RecipeType.MEAT);

    @Mock
    private RecipeRepository recipeRepository;
    @Mock
    private RecipeImageService recipeImageService;
    @Mock
    private RecipeGroupService recipeGroupService;
    @Mock
    private IngredientService ingredientService;
    @Mock
    private WeekplanService weekplanService;
    @Mock
    private ShareService shareService;

    @InjectMocks
    private RecipeService cut;

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

    private static final String testRecipeImageUUID = "duniwqndiu2u912nd9";

    private final AtomicLong ids = new AtomicLong();

    @Test
    void recipeCreated() {
        when(mockRecipe.getOwner()).thenReturn(testUser);
        cut.createNewRecipe(mockRecipe);
        verify(recipeRepository, times(1)).save(mockRecipe);
    }

    @Test
    void recipeGroupCreatedIfNotExistent() {
        when(mockRecipe.getOwner()).thenReturn(testUser);
        when(mockRecipeGroupWithoutId.getId()).thenReturn(null);
        when(mockRecipeGroupWithId.getId()).thenReturn(1L);
        when(recipeGroupService.createRecipeGroup(any())).thenReturn(mockRecipeGroupWithId);
        when(mockRecipe.getRecipeGroups()).thenReturn(List.of(mockRecipeGroupWithoutId));

        cut.createNewRecipe(mockRecipe);

        verify(recipeGroupService, times(1)).createRecipeGroup(mockRecipeGroupWithoutId);
    }

    @Test
    void ingredientCreatedIfNotExistent() {
        when(mockRecipe.getOwner()).thenReturn(testUser);
        when(mockIngredientWithoutId.getId()).thenReturn(null);
        when(mockIngredientNeed.getIngredient()).thenReturn(mockIngredientWithoutId);
        when(mockRecipe.getNeededIngredients()).thenReturn(List.of(mockIngredientNeed));

        cut.createNewRecipe(mockRecipe);

        verify(ingredientService, times(1)).createOrGetIngredient(eq(mockIngredientWithoutId), any());
    }

    @Test
    void recipeDeleted() throws IOException {
        whenRecipeIsLoadableById(recipe("test", 1L));

        cut.deleteRecipe(1L);

        verify(recipeRepository, times(1)).deleteById(1L);
        verify(recipeImageService, times(1)).deleteImage(testRecipeImageUUID);
    }

    @Test
    void recipeDeletionWithdrawsItsShares() {
        var deletedRecipe = recipe("test", 1L);
        whenRecipeIsLoadableById(deletedRecipe);
        when(recipeRepository.getReferenceById(1L)).thenReturn(deletedRecipe);

        cut.deleteRecipe(1L);

        // A share outliving what it points at resolves to nothing, which looks to whoever holds
        // the link like the app is broken rather than like the recipe is gone.
        verify(shareService, times(1)).revokeAllSharesOfRecipe(deletedRecipe);
    }

    @Test
    void recipeDeletionTriggersWeekplanChange() {
        when(weekplanService.getWeekplanDaysByRecipe(1L)).thenReturn(List.of(mockWeekplanDay));
        whenRecipeIsLoadableById(recipe("Test", 1L));

        cut.deleteRecipe(1L);

        verify(weekplanService, times(1)).updateWeekplanDay(mockWeekplanDay);
    }

    @Test
    void recipesAreFuzzySearched() {
        var expectedRecipe = recipe("Poké-Bowl mit Räucherlachs und Gemüse");
        whenSearchableRecipesAre(expectedRecipe,
                recipe("Gebackene Laugen-Käse-Knödel"),
                recipe("Räucherlachs Aprikosen-Curry Sauce"));

        var results = cut.searchUserRecipes(testUser, "Gemüs", MEAT_ONLY);

        assertEquals(expectedRecipe, results.get(0));
    }

    @Test
    void fuzzySearchFindsNoResults() {
        whenSearchableRecipesAre(
                recipe("Poké-Bowl mit Räucherlachs und Gemüse"),
                recipe("Gebackene Laugen-Käse-Knödel"),
                recipe("Räucherlachs Aprikosen-Curry Sauce"));

        var results = cut.searchUserRecipes(testUser, "Tomats", MEAT_ONLY);

        assertTrue(results.isEmpty());
    }

    private Recipe recipe(String title) {
        return recipe(title, ids.incrementAndGet());
    }

    private Recipe recipe(String title, Long id) {
        return Recipe.builder()
                .title(title)
                .id(id)
                .images(List.of(RecipeImage.builder().uuid(testRecipeImageUUID).build()))
                .build();
    }

    private void whenRecipeIsLoadableById(Recipe recipe) {
        when(recipeRepository.getById(recipe.getId())).thenReturn(recipe);
    }

    private void whenSearchableRecipesAre(Recipe... recipes) {
        when(recipeRepository.findByOwnerAndRecipeTypeIn(testUser, MEAT_ONLY)).thenReturn(List.of(recipes));
    }

}
