package com.sterul.opencookbookapiserver.unit.services.recipeimport;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.sterul.opencookbookapiserver.services.recipeimport.recipescrapers.IngredientExtractor;

class IngredientExtractorTest {

    @Test
    void amountsAreExtractedCorrectly()  {
        assertAmount("1EL Butter", 1F);
        assertAmount("1 EL Butter", 1F);
        assertAmount("1 1/2EL Butter", 1.5F);
        assertAmount("1 1/2 EL Butter", 1.5F);
        assertAmount("1 1/2 Butter", 1.5F);
        assertAmount("1½EL  Butter", 1.5F);
        assertAmount("1½ EL  Butter", 1.5F);
        assertAmount("½ EL Butter", 0.5F);
        assertAmount("Butter", 0);
        assertAmount("1.5EL Butter", 1.5F);
        assertAmount("1.5 EL Butter", 1.5F);
        assertAmount("1.5 EL Butter", 1.5F);
        assertAmount("1.5EL Butter", 1.5F);
        assertAmount("1.5 Butter", 1.5F);
        assertAmount("½ Butter", 0.5F);
        assertAmount("Butter ½", 0.5F);
        assertAmount("Butter 2", 2F);
        assertAmount("Butter 2.5", 2.5F);
        assertAmount("Butter 1 1/2", 1.5F);
    }
    @Test
    void unitsAreExtractedCorrectly()  {
        assertUnit("1 Pck. Vanillezucker", "Pck.");
        assertUnit("1 1/2 Butter", "");
        assertUnit("1 1/2EL Butter", "EL");
        assertUnit("1 1/2 EL Butter", "EL");
        assertUnit("1EL Butter", "EL");
        assertUnit("1 EL Butter","EL");
        assertUnit("1½EL  Butter", "EL");
        assertUnit("1½ EL  Butter", "EL");
        assertUnit("½ EL Butter", "EL");
        assertUnit("Butter", "");
        assertUnit("1.5EL Butter", "EL");
        assertUnit("1.5 EL Butter", "EL");
        assertUnit("1.5 EL Butter", "EL");
        assertUnit("1.5EL Butter", "EL");
        assertUnit("1.5 Butter", "");
        assertUnit("½ Butter","");
        assertUnit("Butter ½", "");
        assertUnit("Butter 2", "");
        assertUnit("Butter 2.5", "");
        assertUnit("Butter 1 1/2", "");
        assertUnit("1 wobbelybit Butter", "");
    }


    private void assertAmount(String ingredient, float expectedAmount) {
        assertEquals(expectedAmount, IngredientExtractor.extractAmount(ingredient));
    }

    private void assertUnit(String ingredient, String expectedUnit) {
        assertEquals(expectedUnit, IngredientExtractor.extractUnit(ingredient));
    }

}