package com.sterul.opencookbookapiserver.unit.entities;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.sterul.opencookbookapiserver.entities.Ingredient;
import com.sterul.opencookbookapiserver.entities.IngredientNeed;

class IngredientNeedTest {

    @ParameterizedTest
    @CsvSource({
            "500, g, Flour, 500 g Flour",
            "0.5, l, Milk, 0.5 l Milk",
            // Stripping ".0" anywhere in the number used to turn 1.05 into 15.
            "1.05, kg, Potatoes, 1.05 kg Potatoes",
            "2.03, kg, Sugar, 2.03 kg Sugar",
            "1.5, tsp, Salt, 1.5 tsp Salt",
    })
    void anAmountIsWrittenTheWayItWasMeant(float amount, String unit, String name, String expected) {
        assertThat(need(amount, unit, name).describe()).isEqualTo(expected);
    }

    @Test
    void whatIsNotKnownIsLeftOutRatherThanShowingUpAsAGap() {
        assertThat(need(null, null, "Salt").describe()).isEqualTo("Salt");
        assertThat(need(2f, null, "Eggs").describe()).isEqualTo("2 Eggs");
        assertThat(need(null, "a pinch of", "Pepper").describe()).isEqualTo("a pinch of Pepper");
    }

    @Test
    void aNeedWithoutAnIngredientStillDescribesItself() {
        var need = IngredientNeed.builder().amount(3f).unit("pieces").build();
        assertThat(need.describe()).isEqualTo("3 pieces");
    }

    private static IngredientNeed need(Float amount, String unit, String name) {
        return IngredientNeed.builder()
                .amount(amount)
                .unit(unit)
                .ingredient(Ingredient.builder().name(name).build())
                .build();
    }
}
