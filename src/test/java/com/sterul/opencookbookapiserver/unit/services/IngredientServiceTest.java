package com.sterul.opencookbookapiserver.unit.services;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sterul.opencookbookapiserver.entities.Ingredient;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.repositories.IngredientRepository;
import com.sterul.opencookbookapiserver.services.IngredientMatcher;
import com.sterul.opencookbookapiserver.services.IngredientService;
import com.sterul.opencookbookapiserver.services.exceptions.ElementNotFound;

@ExtendWith(MockitoExtension.class)
class IngredientServiceTest {

    private final CookpalUser testUser = new CookpalUser();

    @Mock
    private IngredientRepository ingredientRepository;
    @Mock
    private IngredientMatcher ingredientMatcher;
    @Mock
    private Ingredient mockIngredient;
    @Mock
    private Ingredient similarPublicIngredient;

    @InjectMocks
    private IngredientService cut;

    @Test
    void ingredientIsCreated() {
        when(ingredientRepository.findByNameAndIsPublicIngredientAndOwner(any(), eq(false), eq(testUser)))
                .thenReturn(null);
        when(ingredientRepository.findByNameAndIsPublicIngredient(any(), eq(true))).thenReturn(null);
        when(ingredientRepository.findAllByIsPublicIngredient(eq(true))).thenReturn(List.of());

        cut.createOrGetIngredient(mockIngredient, testUser);

        verify(ingredientRepository, times(1)).save(mockIngredient);
    }

    @Test
    void newIngredientIsLinkedToSimilarPublicIngredient() throws ElementNotFound {
        when(ingredientMatcher.findIngredientbySimilarName(any(), any())).thenReturn(similarPublicIngredient);

        cut.createOrGetIngredient(mockIngredient, testUser);

        verify(ingredientRepository, times(1)).save(mockIngredient);
        verify(mockIngredient, times(1)).setAliasFor(similarPublicIngredient);
    }

    @Test
    void newIngredientIsNotLinkedToUnsimilarPublicIngredient() throws ElementNotFound {
        when(ingredientMatcher.findIngredientbySimilarName(any(), any())).thenThrow(ElementNotFound.class);

        cut.createOrGetIngredient(mockIngredient, testUser);

        verify(ingredientRepository, times(1)).save(mockIngredient);
        verify(mockIngredient, times(0)).setAliasFor(any());
    }

    @Test
    void privateIngredientIsReused() {
        when(mockIngredient.isPublicIngredient()).thenReturn(false);
        when(ingredientRepository.findByNameAndIsPublicIngredientAndOwner(any(), eq(false), eq(testUser)))
                .thenReturn(mockIngredient);

        cut.createOrGetIngredient(mockIngredient, testUser);

        verify(ingredientRepository, times(0)).save(mockIngredient);
    }

    @Test
    void publicIngredientIsReused() {
        when(ingredientRepository.findByNameAndIsPublicIngredient(any(), eq(true))).thenReturn(mockIngredient);

        cut.createOrGetIngredient(mockIngredient, testUser);

        verify(ingredientRepository, times(0)).save(mockIngredient);
    }
}
