package com.sterul.opencookbookapiserver.unit.services.ingredients;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.sterul.opencookbookapiserver.services.ingredients.IngredientLineReader;
import com.sterul.opencookbookapiserver.services.ingredients.IngredientLineReader.IngredientLine;
import com.sterul.opencookbookapiserver.unit.services.catalogue.ShippedCatalogueDataset;

class IngredientLineReaderTest {

    private final IngredientLineReader cut = ShippedCatalogueDataset.ingredientLineReader();

    @ParameterizedTest
    @CsvSource(delimiter = '|', nullValues = "-", value = {
        "1EL Butter                           | 1    | EL             | Butter",
        "1 1/2EL Butter                       | 1.5  | EL             | Butter",
        "1 1/2 Butter                         | 1.5  | ''             | Butter",
        "1½ EL  Butter                        | 1.5  | EL             | Butter",
        "½ EL Butter                          | 0.5  | EL             | Butter",
        "1.5 Butter                           | 1.5  | ''             | Butter",
        "Butter                               | -    | ''             | Butter",
        "1 Pck. Vanillezucker                 | 1    | Pck.           | Vanillezucker",
        "1 wobbelybit Butter                  | 1    | ''             | wobbelybit Butter",
        "200g Schmelzkäse                     | 200  | g              | Schmelzkäse",
        "100g geriebener Käse zum Überbacken  | 100  | g              | geriebener Käse zum Überbacken",
        "1,5 l Wasser                         | 1.5  | l              | Wasser",
        "1/2 TL Salz                          | 0.5  | TL             | Salz",
        "½ Zitrone                            | 0.5  | ''             | Zitrone",
        "- 2 Eier                             | 2    | ''             | Eier",
        "2-3 Zehen Knoblauch                  | 2    | Zehen          | Knoblauch",
        "200 - 300 ml Brühe                   | 200  | ml             | Brühe",
        "1x Lauch                             | 1    | ''             | Lauch",
        "1x Prise Muskatnuss                  | 1    | Prise          | Muskatnuss",
        "nach Geschmack Pfeffer*              | -    | nach Geschmack | Pfeffer*",
        "Prise Zimt                           | -    | Prise          | Zimt",
        "EL, Olivenöl                         | -    | EL             | Olivenöl",
        "kl. Dose/n Mais                      | -    | kl. Dose/n     | Mais",
        "½ Flasche Rumaroma oder 2 EL Rum     | 0.5  | Flasche        | Rumaroma oder 2 EL Rum",
    })
    void amountUnitAndNameAreReadOff(String written, Float amount, String unit, String name) {
        assertEquals(new IngredientLine(amount, unit, name, ""), read(written));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Schmelzkäse", "4er Pack Eier", "7-Up", "8-Kräuter-Mischung", "Prise", "Zwiebel(n)"})
    void aNameWithoutAmountUnitOrNoteStaysAsWritten(String written) {
        assertEquals(new IngredientLine(null, "", written, ""), read(written));
    }

    @Test
    void aNonBreakingOrThinSpaceSeparatesLikeAnyOther() {
        assertEquals(new IngredientLine(80F, "g", "gehackte Mandeln", ""), read("80 g gehackte Mandeln"));
        assertEquals(new IngredientLine(200F, "g", "Joghurt", ""), read("200 g Joghurt"));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "Mehl (ca. 200 g)   | 200 | g  | Mehl",
        "Käse , ca. 100 g   | 100 | g  | Käse",
        "Mehl 200 g         | 200 | g  | Mehl",
        "Mehl: 1 EL         | 1   | EL | Mehl",
        "Butter - 60 g      | 60  | g  | Butter",
        "Sahne \u2013 200 ml | 200 | ml | Sahne",
        "Mehl \u2014 1 EL    | 1   | EL | Mehl",
        "Eier - 3           | 3   | '' | Eier",
    })
    void anAmountAtTheEndIsReadWithItsUnitOrAfterASeparator(String written, Float amount, String unit, String name) {
        assertEquals(new IngredientLine(amount, unit, name, ""), read(written));
    }

    @Test
    void aBareNumberAfterTheNameIsPartOfIt() {
        assertEquals(new IngredientLine(null, "", "Weizenmehl 550", ""), read("Weizenmehl 550"));
        assertEquals(new IngredientLine(200F, "g", "Weizenmehl 550", ""), read("200g Weizenmehl 550"));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', nullValues = "-", value = {
        "2 EL Butter, weich             | 2   | EL    | Butter       | weich",
        "1,5 EL Butter, weich           | 1.5 | EL    | Butter       | weich",
        "1 Zwiebel, fein gehackt        | 1   | ''    | Zwiebel      | fein gehackt",
        "1 Dose Kichererbsen, abgetropft | 1  | Dose  | Kichererbsen | abgetropft",
        "2 Stück Ingwer (frisch)        | 2   | Stück | Ingwer       | frisch",
        "Stück Ingwer (klein)           | -   | Stück | Ingwer       | klein",
        "100 g Gouda (48 % Fett)        | 100 | g     | Gouda        | 48 % Fett",
        "Kartoffeln (festkochend)       | -   | ''    | Kartoffeln   | festkochend",
        "Zwiebel(n), in Ringen          | -   | ''    | Zwiebel(n)   | in Ringen",
    })
    void whatDoesNotSingleOutAnotherFoodIsANote(String written, Float amount, String unit, String name, String note) {
        assertEquals(new IngredientLine(amount, unit, name, note), read(written));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "400 g Tomaten, passiert        | 400 | g  | Tomaten, passiert",
        "200 g Weizenmehl, Type 550     | 200 | g  | Weizenmehl, Type 550",
        "300 g Spinat (TK)              | 300 | g  | Spinat (TK)",
    })
    void whatSinglesOutAnotherFoodStaysInTheName(String written, Float amount, String unit, String name) {
        assertEquals(new IngredientLine(amount, unit, name, ""), read(written));
    }

    @Test
    void eachPartOfANoteIsJudgedOnItsOwn() {
        assertEquals(new IngredientLine(400F, "g", "Tomaten, passiert", "groß"), read("400 g Tomaten, passiert, groß"));
        assertEquals(new IngredientLine(400F, "g", "Tomaten, passiert", "groß"), read("400 g Tomaten, groß, passiert"));
    }

    @Test
    void partsAfterTheFirstFewAreNoteWithoutAskingTheCatalogue() {
        assertEquals(new IngredientLine(400F, "g", "Tomaten", "groß, reif, frisch, passiert"),
                read("400 g Tomaten, groß, reif, frisch, passiert"));
    }

    @Test
    void englishLinesAreReadTheSameWay() {
        assertEquals(new IngredientLine(2F, "tbsp", "butter", "softened"), cut.read("2 tbsp butter, softened", "en").orElseThrow());
    }

    @Test
    void aSourceGivingTheNoteApartIsReadTheSameWay() {
        assertEquals(new IngredientLine(2F, "EL", "Butter", "weich"), cut.read(2F, "EL", "Butter", ", weich", "de").orElseThrow());
        assertEquals(new IngredientLine(200F, "g", "Mehl", "Type 405"), cut.read(200F, "g", "Mehl", "(Type 405)", "de").orElseThrow());
        assertEquals(new IngredientLine(400F, "g", "Tomaten, passiert", ""), cut.read(400F, "g", "Tomaten", "passiert", "de").orElseThrow());
        assertTrue(cut.read(1F, "", " ", "weich", "de").isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"200 g", ""})
    void aLineThatNamesNothingIsNoIngredient(String written) {
        assertTrue(cut.read(written, "de").isEmpty(), written);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', nullValues = "-", value = {
        "200g Schmelzkäse         | 200 | g          | Schmelzkäse",
        "Prise Zimt               | -   | Prise      | Zimt",
        "kl. Dose/n Mais          | -   | kl. Dose/n | Mais",
        "Mehl (ca. 200 g)         | 200 | g          | Mehl",
        "Käse , ca. 100 g         | 100 | g          | Käse",
        "200 g Butter, weich      | 200 | g          | Butter, weich",
        "Stück Ingwer (klein)     | -   | Stück      | Ingwer (klein)",
    })
    void aNameGivesUpItsAmountAndUnitButKeepsItsNote(String name, Float amount, String unit, String rest) {
        assertEquals(new IngredientLine(amount, unit, rest, ""), cut.quantityOf(name).orElseThrow());
    }

    @ParameterizedTest
    @ValueSource(strings = {"Schmelzkäse", "Gouda (48 % Fett)", "Butter, weich", "4er Pack Eier", "7-Up", "Prise", "200 g"})
    void aNameWithoutAmountOrUnitOrWithNothingElseHasNoQuantity(String name) {
        assertTrue(cut.quantityOf(name).isEmpty(), name);
    }

    @Test
    void oddNumberSignsAreNoFailure() {
        assertEquals(2F, read("2² Butter").amount());
        assertEquals(0F, read("⅟ Butter").amount());
    }

    private IngredientLine read(String written) {
        return cut.read(written, "de").orElseThrow();
    }
}
