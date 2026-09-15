package com.sterul.opencookbookapiserver.unit.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sterul.opencookbookapiserver.entities.Ingredient;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.errors.ApiErrorCode;
import com.sterul.opencookbookapiserver.errors.ApiException;
import com.sterul.opencookbookapiserver.repositories.IngredientRepository;
import com.sterul.opencookbookapiserver.services.IngredientLinker;
import com.sterul.opencookbookapiserver.services.IngredientService;

@ExtendWith(MockitoExtension.class)
class IngredientServiceTest {

    private final CookpalUser owner = new CookpalUser();

    @Mock
    private IngredientRepository ingredientRepository;
    @Mock
    private IngredientLinker linker;

    private IngredientService cut;

    @BeforeEach
    void setup() {
        cut = new IngredientService(ingredientRepository, Optional.of(linker));
    }

    @Test
    void anIngredientOfANewNameIsCreatedForTheOwner() {
        when(ingredientRepository.findByNameAndOwner("Mehl", owner)).thenReturn(Optional.empty());
        when(ingredientRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        cut.createOrGetIngredient(Ingredient.builder().id(99L).name(" Mehl ").additionalInfo("Type 405").build(), owner);

        var saved = ArgumentCaptor.forClass(Ingredient.class);
        verify(ingredientRepository).save(saved.capture());
        assertEquals("Mehl", saved.getValue().getName());
        assertEquals("Type 405", saved.getValue().getAdditionalInfo());
        assertSame(owner, saved.getValue().getOwner());
        assertEquals(null, saved.getValue().getId());
        verify(linker).linkNew(saved.getValue());
    }

    @Test
    void withoutNutritionEstimationANewIngredientStaysUnlinked() {
        var withoutLinker = new IngredientService(ingredientRepository, Optional.empty());
        when(ingredientRepository.findByNameAndOwner("Mehl", owner)).thenReturn(Optional.empty());
        when(ingredientRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var created = withoutLinker.createOrGetIngredient(Ingredient.builder().name("Mehl").build(), owner);

        assertEquals(null, created.getCatalogueFood());
    }

    @Test
    void theOwnersIngredientOfThatNameIsReused() {
        var existing = Ingredient.builder().id(5L).name("Mehl").owner(owner).build();
        when(ingredientRepository.findByNameAndOwner("Mehl", owner)).thenReturn(Optional.of(existing));

        assertSame(existing, cut.createOrGetIngredient(Ingredient.builder().name("Mehl").build(), owner));
        verify(ingredientRepository, never()).save(any());
        verify(linker, never()).linkNew(any());
    }

    @Test
    void anIngredientARecipeUsesIsNotDeleted() {
        var used = Ingredient.builder().id(5L).name("Mehl").owner(owner).build();
        when(ingredientRepository.isUsedByARecipe(used)).thenReturn(true);

        var thrown = assertThrows(ApiException.class, () -> cut.deleteIngredient(used));

        assertEquals(ApiErrorCode.CONFLICT, thrown.getErrorCode());
        verify(ingredientRepository, never()).delete(any());
    }
}
