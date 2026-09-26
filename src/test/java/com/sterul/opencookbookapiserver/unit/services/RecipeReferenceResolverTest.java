package com.sterul.opencookbookapiserver.unit.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sterul.opencookbookapiserver.entities.Ingredient;
import com.sterul.opencookbookapiserver.entities.IngredientNeed;
import com.sterul.opencookbookapiserver.entities.RecipeImage;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.entities.recipe.Recipe;
import com.sterul.opencookbookapiserver.entities.recipe.RecipeGroup;
import com.sterul.opencookbookapiserver.repositories.RecipeGroupRepository;
import com.sterul.opencookbookapiserver.repositories.RecipeImageRepository;
import com.sterul.opencookbookapiserver.services.IngredientService;
import com.sterul.opencookbookapiserver.services.RecipeReferenceResolver;
import com.sterul.opencookbookapiserver.services.exceptions.ElementNotFound;

@ExtendWith(MockitoExtension.class)
class RecipeReferenceResolverTest {

    @Mock
    private IngredientService ingredientService;
    @Mock
    private RecipeGroupRepository recipeGroupRepository;
    @Mock
    private RecipeImageRepository recipeImageRepository;

    @InjectMocks
    private RecipeReferenceResolver cut;

    private final CookpalUser owner = new CookpalUser();

    @Test
    void everyIngredientIsLookedUpAmongTheOwnersIngredients() {
        var named = Ingredient.builder().name("Salz").build();
        var owned = Ingredient.builder().id(5L).name("Salz").build();
        when(ingredientService.createOrGetIngredient(named, owner)).thenReturn(owned);
        var recipe = recipeWith(List.of(IngredientNeed.builder().ingredient(named).build()), List.of(), List.of());

        cut.resolve(recipe, owner);

        assertSame(owned, recipe.getNeededIngredients().get(0).getIngredient());
    }

    @Test
    void aGroupWithoutIdIsCreatedForTheOwner() {
        var created = RecipeGroup.builder().id(3L).title("Dinner").owner(owner).build();
        when(recipeGroupRepository.save(any())).thenReturn(created);
        var recipe = recipeWith(List.of(), List.of(RecipeGroup.builder().title("Dinner").build()), List.of());

        cut.resolve(recipe, owner);

        assertEquals(List.of(created), recipe.getRecipeGroups());
        verify(recipeGroupRepository).save(RecipeGroup.builder().title("Dinner").owner(owner).build());
    }

    @Test
    void aGroupWithIdIsReplacedByTheOwnersStoredGroup() {
        var stored = RecipeGroup.builder().id(3L).title("Stored title").owner(owner).build();
        when(recipeGroupRepository.findByIdAndOwner(3L, owner)).thenReturn(Optional.of(stored));
        var recipe = recipeWith(List.of(), List.of(RecipeGroup.builder().id(3L).title("Sent title").build()), List.of());

        cut.resolve(recipe, owner);

        assertSame(stored, recipe.getRecipeGroups().get(0));
    }

    @Test
    void aGroupTheOwnerDoesNotHaveIsNotFound() {
        when(recipeGroupRepository.findByIdAndOwner(3L, owner)).thenReturn(Optional.empty());
        var recipe = recipeWith(List.of(), List.of(RecipeGroup.builder().id(3L).build()), List.of());

        assertThrows(ElementNotFound.class, () -> cut.resolve(recipe, owner));
        verify(recipeGroupRepository, never()).save(any());
    }

    @Test
    void anImageTheOwnerDoesNotHaveIsNotFound() {
        when(recipeImageRepository.findByUuidAndOwner("foreign", owner)).thenReturn(Optional.empty());
        var recipe = recipeWith(List.of(), List.of(), List.of(RecipeImage.builder().uuid("foreign").build()));

        assertThrows(ElementNotFound.class, () -> cut.resolve(recipe, owner));
    }

    @Test
    void imagesKeepTheirOrder() {
        var first = RecipeImage.builder().uuid("first").owner(owner).build();
        var second = RecipeImage.builder().uuid("second").owner(owner).build();
        when(recipeImageRepository.findByUuidAndOwner("first", owner)).thenReturn(Optional.of(first));
        when(recipeImageRepository.findByUuidAndOwner("second", owner)).thenReturn(Optional.of(second));
        var recipe = recipeWith(List.of(), List.of(),
                List.of(RecipeImage.builder().uuid("first").build(), RecipeImage.builder().uuid("second").build()));

        cut.resolve(recipe, owner);

        assertEquals(List.of(first, second), recipe.getImages());
    }

    private static Recipe recipeWith(List<IngredientNeed> needs, List<RecipeGroup> groups, List<RecipeImage> images) {
        return Recipe.builder()
                .neededIngredients(new ArrayList<>(needs))
                .recipeGroups(new ArrayList<>(groups))
                .images(new ArrayList<>(images))
                .build();
    }
}
