package com.sterul.opencookbookapiserver.integration;

import static org.junit.Assert.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.sterul.opencookbookapiserver.entities.Ingredient;
import com.sterul.opencookbookapiserver.repositories.UserRepository;
import com.sterul.opencookbookapiserver.services.IngredientService;
import com.sterul.opencookbookapiserver.services.exceptions.ElementNotFound;

@SpringBootTest
@ActiveProfiles("integration-test")
class IngredientServiceTest extends IntegrationTest {

    @Autowired
    private IngredientService cut;

    @Autowired
    private UserRepository userRepository;

    @Test
    @Transactional
    void test1() throws ElementNotFound {
        TestUtils.whenAuthenticated(userRepository);
        var testUser = TestUtils.getTestUser(userRepository);
        var publicIngredient = cut.createPublicIngredient(Ingredient.builder()
            .name("TestPublic")
            .nutrientsEnergy(1000F)
            .build());

        var ingredient = cut.createOrGetIngredient(Ingredient.builder()
            .name("Test")
            .aliasFor(publicIngredient)
            .build(), testUser);

        var savedIngredient = cut.getIngredient(ingredient.getId());
        assertEquals(Float.valueOf(1000F), savedIngredient.getNutrientsEnergy());
    }
}
