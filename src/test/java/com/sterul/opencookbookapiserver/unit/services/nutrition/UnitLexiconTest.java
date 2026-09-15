package com.sterul.opencookbookapiserver.unit.services.nutrition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import com.sterul.opencookbookapiserver.services.nutrition.UnitLexicon;
import com.sterul.opencookbookapiserver.services.nutrition.dataset.NutritionDataset;

class UnitLexiconTest {

    private final UnitLexicon lexicon = ShippedNutritionDataset.UNIT_LEXICON;

    /** Every unit the app offered before units came from the dataset; recipes are written in them. */
    static Stream<String> unitsOfEarlierReleases() {
        return Stream.of(
            "Becher",
            "Beet/e",
            "Beutel",
            "Blatt",
            "Blätter",
            "Bund",
            "Bündel",
            "cl",
            "cm",
            "dicke",
            "dl",
            "Dose",
            "Dose/n",
            "dünne",
            "Ecke(n)",
            "Eimer",
            "einige",
            "einige Stiele",
            "EL",
            "EL gehäuft",
            "EL gestr.",
            "etwas",
            "evtl.",
            "extra",
            "Fässchen",
            "Fläschchen",
            "Flasche",
            "Flaschen",
            "g",
            "Glas",
            "Gläser",
            "gr. Dose/n",
            "gr. Flasche(n)",
            "gr. Glas",
            "gr. Gläser",
            "gr. Kopf",
            "gr. Scheibe(n)",
            "gr. Stück(e)",
            "große",
            "großen",
            "großer",
            "großes",
            "halbe",
            "Halm(e)",
            "Handvoll",
            "Kästchen",
            "kg",
            "kl. Bund",
            "kl. Dose/n",
            "kl. Flasche/n",
            "kl. Glas",
            "kl. Gläser",
            "kl. Kopf",
            "kl. Scheibe(n)",
            "kl. Stange(n)",
            "kl. Stück(e)",
            "kleine",
            "kleiner",
            "kleines",
            "Knolle/n",
            "Kopf",
            "Köpfe",
            "Körner",
            "Kugel",
            "Kugel/n",
            "Kugeln",
            "Liter",
            "m.-große",
            "m.-großer",
            "m.-großes",
            "mehr",
            "mg",
            "ml",
            "Msp.",
            "n. B.",
            "Paar",
            "Paket",
            "Pck.",
            "Pkt.",
            "Platte/n",
            "Port.",
            "Prise(n)",
            "Prisen",
            "Prozent %",
            "Riegel",
            "Ring/e",
            "Rippe/n",
            "Rispe(n)",
            "Rolle(n)",
            "Schälchen",
            "Scheibe/n",
            "Schuss",
            "Spritzer",
            "Stange/n",
            "Stängel",
            "Staude(n)",
            "Stick(s)",
            "Stiel/e",
            "Stiele",
            "Streifen",
            "Stück(e)",
            "Tablette(n)",
            "Tafel",
            "Tafeln",
            "Tasse",
            "Tasse/n",
            "Teil/e",
            "TL",
            "TL gehäuft",
            "TL gestr.",
            "Topf",
            "Tropfen",
            "Tube/n",
            "Tüte/n",
            "viel",
            "wenig",
            "Würfel",
            "Wurzel",
            "Wurzel/n",
            "Zehe/n",
            "Zweig/e");
    }

    @ParameterizedTest
    @MethodSource("unitsOfEarlierReleases")
    void aUnitOfAnEarlierReleaseIsStillKnown(String unit) {
        assertTrue(lexicon.isKnownUnit(unit), unit);
    }

    @Test
    void theWordsOfEveryLanguageStandForTheSameUnit() {
        assertEquals("tablespoon", key("EL"));
        assertEquals("tablespoon", key("Esslöffel"));
        assertEquals("tablespoon", key("tbsp"));
    }

    @Test
    void capitalisationAndSpacingDoNotMatter() {
        assertEquals("teaspoon", key("  tl  "));
        assertEquals(key("TL gehäuft"), key("tl   gehäuft"));
    }

    @Test
    void anUnknownWordIsNoUnit() {
        assertFalse(lexicon.isKnownUnit("Schnapsglas"));
        assertFalse(lexicon.isKnownUnit(null));
    }

    @Test
    void aUnitIsFoundByItsKey() {
        assertEquals(NutritionDataset.UnitKind.MASS, lexicon.unit("gram").orElseThrow().kind());
    }

    private String key(String word) {
        return lexicon.resolve(word).orElseThrow().key();
    }
}
