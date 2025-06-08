package com.sterul.opencookbookapiserver.unit.services;

import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import com.sterul.opencookbookapiserver.entities.Ingredient;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.repositories.IngredientRepository;
import com.sterul.opencookbookapiserver.services.IngredientMatcher;
import com.sterul.opencookbookapiserver.services.IngredientService;
import com.sterul.opencookbookapiserver.services.exceptions.ElementNotFound;

@SpringBootTest
@ActiveProfiles("unit-test")
class IngredientServiceTest {

    private final CookpalUser testUser = new CookpalUser();
    @Autowired
    private IngredientService cut;
    @MockBean
    private IngredientRepository ingredientRepository;
    @Mock
    private Ingredient mockIngredient;
    @Mock
    private Ingredient mockIngredient2;
    @MockBean
    private IngredientMatcher mockIngredientMatcher;

    @BeforeEach
    void setup() {
        when(mockIngredient.isPublicIngredient()).thenReturn(false);
        when(mockIngredient2.isPublicIngredient()).thenReturn(true);
    }

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
        when(mockIngredientMatcher.findIngredientbySimilarName(any(), any())).thenReturn(mockIngredient2);
        cut.createOrGetIngredient(mockIngredient, testUser);
        verify(ingredientRepository, times(1)).save(mockIngredient);
        verify(mockIngredient, times(1)).setAliasFor(mockIngredient2);
    }

    @Test
    void newIngredientIsNotLinkedToUnsimilarPublicIngredient() throws ElementNotFound {
        when(mockIngredientMatcher.findIngredientbySimilarName(any(), any())).thenThrow(ElementNotFound.class);
        cut.createOrGetIngredient(mockIngredient, testUser);
        verify(ingredientRepository, times(1)).save(mockIngredient);
        verify(mockIngredient, times(0)).setAliasFor(any());
    }

    @Test
    void privateIngredientIsReused() {
        when(ingredientRepository.findByNameAndIsPublicIngredientAndOwner(any(), eq(false), eq(testUser)))
                .thenReturn(mockIngredient);
        cut.createOrGetIngredient(mockIngredient, testUser);
        verify(ingredientRepository, times(0)).save(mockIngredient);
    }

    @Test
    void publicIngredientIsReused() {
        when(ingredientRepository.findByNameAndIsPublicIngredientAndOwner(any(), eq(false), eq(testUser)))
                .thenReturn(null);
        when(ingredientRepository.findByNameAndIsPublicIngredient(any(), eq(true))).thenReturn(mockIngredient);
        cut.createOrGetIngredient(mockIngredient, testUser);
        verify(ingredientRepository, times(0)).save(mockIngredient);
    }

    @Test
    void ingredientIsFuzzyFound() {
        assertFuzzySeachMatchesIngredient("Bratwürste", "Bratwürstchen", true);
        assertFuzzySeachMatchesIngredient("Lauchzwiebel(n)", "Lauchzwiebel", true);
        assertFuzzySeachMatchesIngredient("Salz und Pfeffer", "Salz & Pfeffer", true);
        assertFuzzySeachMatchesIngredient("Salz", "Salz*", true);

    }

    @Test
    void differentIngredientsAreNotFuzzyFound() {
        assertFuzzySeachMatchesIngredient("Brötchen", "Brokkoli", false);
        assertFuzzySeachMatchesIngredient("Lachs (Sashimi-Qualität)", "Lachs", false);
        assertFuzzySeachMatchesIngredient("Paprikapulver , scharf", "Paprikapulver", false);
    }

    private void assertFuzzySeachMatchesIngredient(String s, String s1, boolean shouldMatch) {
        when(mockIngredient.getName()).thenReturn(s);
        when(ingredientRepository.findAllByIsPublicIngredientAndOwner(false, testUser))
                .thenReturn(Arrays.asList(mockIngredient));
        try {
            var result = cut.findUserIngredientBySimilarName(s1, testUser);
            if (result == mockIngredient && !shouldMatch) {
                fail();
            }
        } catch (ElementNotFound e) {
            if (shouldMatch) {
                fail();
            }
        }

    }
}
