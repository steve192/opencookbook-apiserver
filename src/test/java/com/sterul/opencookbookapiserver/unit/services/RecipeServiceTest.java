package com.sterul.opencookbookapiserver.unit.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.sterul.opencookbookapiserver.entities.RecipeImage;
import com.sterul.opencookbookapiserver.entities.WeekplanDay;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.entities.recipe.Diet;
import com.sterul.opencookbookapiserver.entities.recipe.Recipe;
import com.sterul.opencookbookapiserver.entities.recipe.RecipeGroup;
import com.sterul.opencookbookapiserver.repositories.RecipeRepository;
import com.sterul.opencookbookapiserver.services.RecipeImageService;
import com.sterul.opencookbookapiserver.services.RecipeReferenceResolver;
import com.sterul.opencookbookapiserver.services.RecipeService;
import com.sterul.opencookbookapiserver.services.WeekplanService;
import com.sterul.opencookbookapiserver.services.classification.ClassificationProvenance;
import com.sterul.opencookbookapiserver.services.exceptions.ElementNotFound;
import com.sterul.opencookbookapiserver.services.sharing.ShareService;

@ExtendWith(MockitoExtension.class)
class RecipeServiceTest {

    private static final List<Diet> MEAT_ONLY = List.of(Diet.MEAT);

    @Mock
    private ApplicationEventPublisher events;
    @Mock
    private ClassificationProvenance provenance;
    @Mock
    private RecipeRepository recipeRepository;
    @Mock
    private RecipeImageService recipeImageService;
    @Mock
    private RecipeReferenceResolver recipeReferenceResolver;
    @Mock
    private WeekplanService weekplanService;
    @Mock
    private ShareService shareService;

    @InjectMocks
    private RecipeService cut;

    @Mock
    private Recipe mockRecipe;
    @Mock
    private WeekplanDay mockWeekplanDay;

    @Mock
    private CookpalUser testUser;

    private static final String testRecipeImageUUID = "duniwqndiu2u912nd9";

    private final AtomicLong ids = new AtomicLong();

    @Test
    void recipeCreatedWithItsReferencesResolvedForItsOwner() throws ElementNotFound {
        when(mockRecipe.getOwner()).thenReturn(testUser);

        cut.createNewRecipe(mockRecipe);

        var inOrder = inOrder(recipeReferenceResolver, recipeRepository);
        inOrder.verify(recipeReferenceResolver).resolve(mockRecipe, testUser);
        inOrder.verify(recipeRepository).save(mockRecipe);
    }

    @Test
    void updatedRecipeKeepsOwnerAndSourceAndIsResolvedForTheOwner() throws ElementNotFound {
        var existing = recipe("Stored", 7L);
        existing.setOwner(testUser);
        existing.setRecipeSource("https://example.com/recipe");
        whenRecipeIsLoadableById(existing);
        var update = recipe("Changed", 7L);

        cut.updateSingleRecipe(update);

        assertEquals(testUser, update.getOwner());
        assertEquals("https://example.com/recipe", update.getRecipeSource());
        verify(recipeReferenceResolver).resolve(update, testUser);
        verify(recipeRepository).save(update);
    }

    @Test
    void removingAGroupTakesOnlyThatGroupOffItsRecipes() {
        var removed = RecipeGroup.builder().id(1L).title("Removed").build();
        var kept = RecipeGroup.builder().id(2L).title("Kept").build();
        var filed = recipe("Filed", 3L);
        filed.setRecipeGroups(new ArrayList<>(List.of(removed, kept)));
        when(recipeRepository.findByRecipeGroups(removed)).thenReturn(List.of(filed));

        cut.removeRecipeGroupFromRecipes(removed);

        assertEquals(List.of(kept), filed.getRecipeGroups());
        verify(recipeRepository).save(filed);
    }

    @Test
    void recipeDeleted() throws IOException, ElementNotFound {
        whenRecipeIsLoadableById(recipe("test", 1L));

        cut.deleteRecipe(1L);

        verify(recipeRepository, times(1)).deleteById(1L);
        verify(recipeImageService, times(1)).deleteImage(testRecipeImageUUID);
    }

    @Test
    void recipeDeletionWithdrawsItsShares() throws ElementNotFound {
        var deletedRecipe = recipe("test", 1L);
        whenRecipeIsLoadableById(deletedRecipe);

        cut.deleteRecipe(1L);

        // A share outliving what it points at resolves to nothing, which looks to whoever holds
        // the link like the app is broken rather than like the recipe is gone.
        verify(shareService, times(1)).revokeAllSharesOfRecipe(deletedRecipe);
    }

    @Test
    void recipeDeletionTriggersWeekplanChange() throws ElementNotFound {
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
        when(recipeRepository.findById(recipe.getId())).thenReturn(Optional.of(recipe));
    }

    private void whenSearchableRecipesAre(Recipe... recipes) {
        when(recipeRepository.findByOwnerAndRecipeTypeIn(testUser, MEAT_ONLY)).thenReturn(List.of(recipes));
    }

}
