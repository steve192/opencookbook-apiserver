package com.sterul.opencookbookapiserver.unit.services.ingredients;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.sterul.opencookbookapiserver.services.ingredients.IngredientNameSplitter;
import com.sterul.opencookbookapiserver.services.ingredients.IngredientNameSplitter.SplitName;
import com.sterul.opencookbookapiserver.unit.services.nutrition.ShippedNutritionDataset;

class IngredientNameSplitterTest {

    private final IngredientNameSplitter splitter = new IngredientNameSplitter(ShippedNutritionDataset.UNIT_LEXICON);

    @ParameterizedTest
    @CsvSource(delimiter = '|', nullValues = "-", value = {
        "200g Schmelzkäse                     | Schmelzkäse                     | 200  | g",
        "100g geriebener Käse zum Überbacken  | geriebener Käse zum Überbacken  | 100  | g",
        "1,5 l Wasser                         | Wasser                          | 1.5  | l",
        "500ml Milch                          | Milch                           | 500  | ml",
        "1/2 TL Salz                          | Salz                            | 0.5  | TL",
        "½ Zitrone                            | Zitrone                         | 0.5  | -",
        "- 2 Eier                             | Eier                            | 2    | -",
        "2-3 Zehen Knoblauch                  | Knoblauch                       | 2    | Zehen",
        "1x Lauch                             | Lauch                           | 1    | -",
        "0.5x Zwiebel                         | Zwiebel                         | 0.5  | -",
        "1x Prise Muskatnuss                  | Muskatnuss                      | 1    | Prise",
        "nach Geschmack Pfeffer*              | Pfeffer*                        | -    | nach Geschmack",
        "Prise Zimt                           | Zimt                            | -    | Prise",
        "Stück Ingwer (klein)                 | Ingwer (klein)                  | -    | Stück",
        "El Sojasauce                         | Sojasauce                       | -    | El",
        "EL, Olivenöl                         | Olivenöl                        | -    | EL",
        "Packung stückige Tomaten             | stückige Tomaten                | -    | Packung",
        "kl. Dose/n Mais                      | Mais                            | -    | kl. Dose/n",
        "Mehl (ca. 200 g)                     | Mehl                            | 200  | g",
        "Käse , ca. 100 g                     | Käse                            | 100  | g",
    })
    void theAmountAndUnitANameHoldsAreSplitOff(String written, String name, Float amount, String unit) {
        assertEquals(new SplitName(name, amount, unit), splitter.split(written).orElseThrow());
    }

    @ParameterizedTest
    @ValueSource(strings = {"Schmelzkäse", "Gouda (48 % Fett)", "4er Pack Eier", "7-Up", "8-Kräuter-Mischung", "Prise", "200 g",
        "Kartoffeln (festkochend)", "Salz, Pfeffer"})
    void aNameHoldingNothingToSplitOffOrNothingElseStays(String written) {
        assertTrue(splitter.split(written).isEmpty(), written);
    }
}
