package com.sterul.opencookbookapiserver.unit.services;

import com.sterul.opencookbookapiserver.entities.Ingredient;
import com.sterul.opencookbookapiserver.entities.account.User;
import com.sterul.opencookbookapiserver.repositories.IngredientRepository;
import com.sterul.opencookbookapiserver.services.IngredientService;
import com.sterul.opencookbookapiserver.services.exceptions.ElementNotFound;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.util.Arrays;

import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
class IngredientServiceTest {

    private final User testUser = new User();
    @Autowired
    private IngredientService cut;
    @MockBean
    private IngredientRepository ingredientRepository;
    @Mock
    private Ingredient mockIngredient;

    @Test
    void ingredientIsCreated() {
        when(ingredientRepository.findByNameAndIsPublicIngredientAndOwner(any(), eq(false), eq(testUser))).thenReturn(null);
        when(ingredientRepository.findByNameAndIsPublicIngredient(any(), eq(true))).thenReturn(null);
        cut.createOrGetIngredient(mockIngredient, testUser);
        verify(ingredientRepository, times(1)).save(mockIngredient);
    }

    @Test
    void privateIngredientIsReused() {
        when(ingredientRepository.findByNameAndIsPublicIngredientAndOwner(any(), eq(false), eq(testUser))).thenReturn(mockIngredient);
        cut.createOrGetIngredient(mockIngredient, testUser);
        verify(ingredientRepository, times(0)).save(mockIngredient);
    }

    @Test
    void publicIngredientIsReused() {
        when(ingredientRepository.findByNameAndIsPublicIngredientAndOwner(any(), eq(false), eq(testUser))).thenReturn(null);
        when(ingredientRepository.findByNameAndIsPublicIngredient(any(), eq(true))).thenReturn(mockIngredient);
        cut.createOrGetIngredient(mockIngredient, testUser);
        verify(ingredientRepository, times(0)).save(mockIngredient);
    }

    @Test
    void ingredientIsFuzzyFound() {
        assertFuzzySeachMatchesIngredient("Bratw??rste", "Bratw??rstchen", true);
        assertFuzzySeachMatchesIngredient("Lauchzwiebel(n)", "Lauchzwiebel", true);
        assertFuzzySeachMatchesIngredient("Salz und Pfeffer", "Salz & Pfeffer", true);
        assertFuzzySeachMatchesIngredient("Salz", "Salz*", true);

    }

    @Test
    void differentIngredientsAreNotFuzzyFound() {
        assertFuzzySeachMatchesIngredient("Br??tchen", "Brokkoli", false);
        assertFuzzySeachMatchesIngredient("Lachs (Sashimi-Qualit??t)", "Lachs", false);
        assertFuzzySeachMatchesIngredient("Paprikapulver , scharf", "Paprikapulver", false);
    }

    private void assertFuzzySeachMatchesIngredient(String s, String s1, boolean shouldMatch) {
        when(mockIngredient.getName()).thenReturn(s);
        when(ingredientRepository.findAllByIsPublicIngredientAndOwner(false, testUser)).thenReturn(Arrays.asList(mockIngredient));
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
