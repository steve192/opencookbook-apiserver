package com.sterul.opencookbookapiserver.unit.services;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sterul.opencookbookapiserver.entities.Ingredient;
import com.sterul.opencookbookapiserver.repositories.IngredientRepository;
import com.sterul.opencookbookapiserver.services.IngredientService;

import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
public class IngredientServiceTest {

    @Autowired
    private IngredientService cut;

    @MockBean
    private IngredientRepository ingredientRepository;

    @Mock
    private Ingredient mockIngredient;

    @Test
    void ingredientIsCreated() {
        when(ingredientRepository.findByName(any())).thenReturn(null);
        cut.createOrGetIngredient(mockIngredient);
        verify(ingredientRepository, times(1)).save(mockIngredient);
    }

    @Test
    void ingredientIsReused() {
        when(ingredientRepository.findByName(any())).thenReturn(mockIngredient);
        cut.createOrGetIngredient(mockIngredient);
        verify(ingredientRepository, times(0)).save(mockIngredient);
    }
}
