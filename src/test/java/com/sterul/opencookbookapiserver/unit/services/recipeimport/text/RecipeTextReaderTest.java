package com.sterul.opencookbookapiserver.unit.services.recipeimport.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.sterul.opencookbookapiserver.services.recipeimport.text.RecipeText;
import com.sterul.opencookbookapiserver.services.recipeimport.text.RecipeTextReader;
import com.sterul.opencookbookapiserver.unit.services.catalogue.ShippedCatalogueDataset;

/**
 * Reading recipes from texts as people write them in captions, notes and messages. Every caption
 * here is made up for the pattern it stands for.
 */
class RecipeTextReaderTest {

    private static final String EM_DASH = Character.toString(0x2014);
    private static final String EN_DASH = Character.toString(0x2013);
    private static final String NBSP = Character.toString(0x00A0);
    private static final String WORD_JOINER = Character.toString(0x2060);
    private static final String INVISIBLE_SEPARATOR = Character.toString(0x2063);
    private static final String BRAILLE_BLANK = Character.toString(0x2800);
    private static final String LINE_SEPARATOR = Character.toString(0x2028);
    private static final String FRACTION_SLASH = Character.toString(0x2044);

    private final RecipeTextReader cut = new RecipeTextReader(ShippedCatalogueDataset.UNIT_LEXICON);

    private RecipeText read(String text) {
        var recipe = cut.read(text);
        assertTrue(recipe.isRecipe(), "No recipe read from:\n" + text);
        return recipe;
    }

    private static String keycap(int digit) {
        return digit + Character.toString(0xFE0F) + Character.toString(0x20E3);
    }

    @Nested
    class Headings {

        @Test
        void aHeadingWithAColonStartsTheIngredientsAndAStepHeadingTheMethod() {
            var recipe = read("""
                    Kartoffelsalat

                    Zutaten:
                    1 kg Kartoffeln
                    2 EL Essig
                    Salz

                    Zubereitung:
                    Kartoffeln kochen und pellen.
                    Mit Essig und Salz mischen.
                    """);

            assertEquals("Kartoffelsalat", recipe.title());
            assertEquals(List.of("1 kg Kartoffeln", "2 EL Essig", "Salz"), recipe.ingredientLines());
            assertEquals(List.of("Kartoffeln kochen und pellen.", "Mit Essig und Salz mischen."), recipe.steps());
        }

        @Test
        void headingsWithoutAColonAndWithEmojiAreHeadingsToo() {
            var recipe = read("""
                    ✨INGREDIENTS
                    200 g pasta
                    1 tbsp olive oil

                    🍽️ Method
                    Boil the pasta.
                    """);

            assertEquals(List.of("200 g pasta", "1 tbsp olive oil"), recipe.ingredientLines());
            assertEquals(List.of("Boil the pasta."), recipe.steps());
        }

        @Test
        void aLineThatEndsInWhatYouNeedIsAHeading() {
            var recipe = read("""
                    Für 8 Waffeln brauchst du:
                    250 g Mehl
                    2 Eier
                    """);

            assertEquals(List.of("250 g Mehl", "2 Eier"), recipe.ingredientLines());
            assertEquals(8, recipe.servings());
        }

        @ParameterizedTest
        @CsvSource(delimiter = ';', value = {
            "Zutaten (für 4 Portionen):;4",
            "Ingredients (makes 12);12",
            "Zutaten für 6 Personen;6",
            "Ingredients (2 servings):;2",
            "🍽️ Zutaten für ca. 3 Gläser;3",
        })
        void theServingsAreTakenFromTheHeading(String heading, int servings) {
            var recipe = read(heading + "\n200 g Mehl\n2 Eier\n");

            assertEquals(servings, recipe.servings());
        }

        @ParameterizedTest
        @CsvSource(delimiter = ';', value = {
            "Für 3 Personen;3",
            "Serves 5;5",
            "This makes 10 cookies.;10",
        })
        void orFromALineAboveTheIngredients(String line, int servings) {
            var recipe = read("Cookies\n" + line + "\n\nIngredients:\n200 g flour\n100 g butter\n");

            assertEquals(servings, recipe.servings());
        }

        @Test
        void aRecipeThatStatesNoServingsHasNone() {
            assertNull(read("Zutaten (30 cm Form):\n200 g Mehl\n").servings());
        }
    }

    @Nested
    class IngredientLines {

        @Test
        void bulletsGoWhetherDashesDotsOrEmoji() {
            var recipe = read("Zutaten:\n☑️ 2 Eier\n- 200 g Mehl\n• 1 Prise Salz\n" + EN_DASH + " 100 ml Milch\n·1 TL Zucker\n");

            assertEquals(List.of("2 Eier", "200 g Mehl", "1 Prise Salz", "100 ml Milch", "1 TL Zucker"),
                    recipe.ingredientLines());
        }

        @Test
        void aLineOfThousandsOfBulletsIsReadWithoutOverflowingTheStack() {
            var recipe = read("Zutaten:\n" + "- ".repeat(4_000) + "200 g Mehl\n");

            assertEquals(List.of("200 g Mehl"), recipe.ingredientLines());
        }

        @Test
        void anAmountWrittenLastIsPutFirst() {
            var recipe = read("Zutaten:\nMehl " + EM_DASH + " 1 EL\nButter: 60 g\nSahne " + EN_DASH
                    + " 200 ml\nPaprika (rot) - 2\nCrème fraîche " + EM_DASH + " 1 Packung\n");

            assertEquals(List.of("1 EL Mehl", "60 g Butter", "200 ml Sahne", "2 Paprika (rot)", "1 Packung Crème fraîche"),
                    recipe.ingredientLines());
        }

        @Test
        void unitsGluedToTheAmountCountAsQuantities() {
            var recipe = read("250g Quark\n100ml Milch\n2EL Honig\n");

            assertEquals(List.of("250g Quark", "100ml Milch", "2EL Honig"), recipe.ingredientLines());
        }

        @Test
        void fractionsOfEveryKindBecomeOneShape() {
            var recipe = read("Ingredients:\n½ tsp salt\n1½ cups flour\n" + WORD_JOINER + "1 1" + FRACTION_SLASH
                    + "2 tbsp honey\n¾ cup sugar\n2-3 tbsp oil\n");

            assertEquals(List.of("1/2 tsp salt", "1 1/2 cups flour", "1 1/2 tbsp honey", "3/4 cup sugar", "2-3 tbsp oil"),
                    recipe.ingredientLines());
        }

        @Test
        void groupHeadingsGoTheRecipeHasNoGroups() {
            var recipe = read("""
                    Zutaten:
                    Für den Teig:
                    200 g Mehl
                    100 g Butter

                    Für die Füllung
                    250 g Quark

                    Zum Bestreuen:
                    Puderzucker

                    Sauce
                    • 2 EL Mayonnaise
                    • 1 TL Senf

                    Zutaten für den Guss:
                    100 g Schokolade
                    """);

            assertEquals(List.of("200 g Mehl", "100 g Butter", "250 g Quark", "Puderzucker", "2 EL Mayonnaise",
                    "1 TL Senf", "100 g Schokolade"), recipe.ingredientLines());
        }

        @Test
        void aListAfterTheColonOfTheHeadingIsSplitOnCommasOutsideBrackets() {
            var recipe = read("""
                    Du brauchst: 50 g Haferflocken, 100 ml Milch (oder Hafermilch, Sojamilch), 1 TL Honig
                    So geht's: Alles verrühren und kühl stellen.
                    """);

            assertEquals(List.of("50 g Haferflocken", "100 ml Milch (oder Hafermilch, Sojamilch)", "1 TL Honig"),
                    recipe.ingredientLines());
            assertEquals(List.of("Alles verrühren und kühl stellen."), recipe.steps());
        }

        @Test
        void aListOfShortNamesWithoutAmountsIsOneIngredientEach() {
            var recipe = read("Zutaten:\n200 g Mehl\nSalz, Pfeffer, Paprika\nsel / poivre / thym\nFresh basil, sliced\n");

            assertEquals(List.of("200 g Mehl", "Salz", "Pfeffer", "Paprika", "sel", "poivre", "thym", "Fresh basil, sliced"),
                    recipe.ingredientLines());
        }

        @Test
        void ingredientsJoinedByAPlusAreSeparate() {
            var recipe = read("Zutaten:\n1 EL Öl + 1 TL Salz + Pfeffer\n2 EL Sesam (hell+schwarz)\n");

            assertEquals(List.of("1 EL Öl", "1 TL Salz", "Pfeffer", "2 EL Sesam (hell+schwarz)"), recipe.ingredientLines());
        }

        @Test
        void handlesGoFromIngredientLines() {
            var recipe = read("Zutaten:\n1 Hähnchenbrust @testfarm\n2 EL Öl (am liebsten von @testmill )\n");

            assertEquals(List.of("1 Hähnchenbrust", "2 EL Öl (am liebsten von )"), recipe.ingredientLines());
        }

        @Test
        void anExclamationInBracketsDoesNotEndTheList() {
            var recipe = read("Zutaten:\nNudeln vom Wochenmarkt (unbedingt probieren!)\n200 g Hackfleisch\n");

            assertEquals(List.of("Nudeln vom Wochenmarkt (unbedingt probieren!)", "200 g Hackfleisch"),
                    recipe.ingredientLines());
        }
    }

    @Nested
    class Steps {

        @Test
        void numberedLinesAreSteps() {
            var recipe = read("""
                    Zutaten:
                    200 g Mehl

                    Anleitung
                    Vorab alles bereitstellen.
                    1. Ofen vorheizen.
                    2.Teig rühren.
                    3) Backen.
                    Step 4: Abkühlen lassen.
                    """);

            assertEquals(List.of("Ofen vorheizen.", "Teig rühren.", "Backen.", "Abkühlen lassen."), recipe.steps());
        }

        @Test
        void keycapNumbersAreNumbersAndALeadIsJoinedToItsLines() {
            var recipe = read("Zutaten:\n500 ml Milch\n\n" + keycap(1) + " Teig anrühren\nMehl und Milch verrühren.\n"
                    + "Kurz ruhen lassen.\n\n" + keycap(2) + "Ausbacken\nIn der Pfanne goldbraun backen.\n\n"
                    + "Am liebsten warm essen.\n");

            assertEquals(List.of("Teig anrühren: Mehl und Milch verrühren. Kurz ruhen lassen.",
                    "Ausbacken: In der Pfanne goldbraun backen."), recipe.steps());
        }

        @Test
        void bulletedLinesAreSteps() {
            var recipe = read("Ingredients\n2 eggs\n\nInstructions\n- Whisk the eggs.\n• Fry them in butter.\n");

            assertEquals(List.of("Whisk the eggs.", "Fry them in butter."), recipe.steps());
        }

        @Test
        void withoutNumbersOrBulletsEveryLineIsAStepUnlessItRunsOn() {
            var recipe = read("""
                    Zutaten
                    200 g Nudeln

                    Zubereitung

                    Nudeln kochen.

                    Die Sauce aufkochen lassen und dann
                    über die Nudeln geben.
                    Mit Käse bestreuen.
                    Fertig
                    """);

            assertEquals(List.of("Nudeln kochen.", "Die Sauce aufkochen lassen und dann über die Nudeln geben.",
                    "Mit Käse bestreuen.", "Fertig"), recipe.steps());
        }

        @Test
        void withoutAStepHeadingTheMethodFollowsTheIngredients() {
            var recipe = read("""
                    Zutaten:
                    2 Paprika
                    1 Zwiebel

                    Paprika und Zwiebel schneiden und anbraten.

                    Mit Salz würzen.
                    """);

            assertEquals(List.of("2 Paprika", "1 Zwiebel"), recipe.ingredientLines());
            assertEquals(List.of("Paprika und Zwiebel schneiden und anbraten.", "Mit Salz würzen."), recipe.steps());
        }

        @Test
        void aSectionTheLexiconDoesNotKnowEndsTheIngredients() {
            var recipe = read("""
                    Ingredients
                    - 2 eggs
                    - 1 tbsp butter

                    Cooking Notes
                    - Whisk the eggs well. Do not overcook them
                    - Serve hot with toast and a little salt
                    """);

            assertEquals(List.of("2 eggs", "1 tbsp butter"), recipe.ingredientLines());
            assertEquals(List.of("Whisk the eggs well. Do not overcook them", "Serve hot with toast and a little salt"),
                    recipe.steps());
        }

        @Test
        void greetingsClosersAndQuestionsAreNoSteps() {
            var recipe = read("""
                    Zutaten:
                    2 Eier

                    Zubereitung:
                    Hallo ihr Lieben,
                    Eier aufschlagen und verquirlen.
                    In der Pfanne stocken lassen. Guten Appetit!

                    Wer probiert es aus?
                    Lasst es euch schmecken!
                    """);

            assertEquals(List.of("Eier aufschlagen und verquirlen.", "In der Pfanne stocken lassen."), recipe.steps());
        }
    }

    @Nested
    class Noise {

        @Test
        void theClosingTagsAndWhateverFollowsThemGo() {
            var recipe = read("""
                    Zutaten:
                    200 g Mehl

                    1. Teig kneten. #backen
                    2. Backen.

                    #kuchen #backen #rezept #lecker
                    .
                    KUCHENREZEPTE
                    BACKEN MIT KINDERN
                    [Kuchen, Backen, Rezept]
                    """);

            assertEquals(List.of("Teig kneten.", "Backen."), recipe.steps());
        }

        @Test
        void tagsBeforeAListWithoutHeadingAreNotTheEnd() {
            var recipe = read("""
                    Ofenkartoffeln!

                    #ofen #kartoffeln #rezept

                    1 kg Kartoffeln
                    2 EL Öl
                    1 TL Salz
                    """);

            assertEquals("Ofenkartoffeln", recipe.title());
            assertEquals(List.of("1 kg Kartoffeln", "2 EL Öl", "1 TL Salz"), recipe.ingredientLines());
        }

        @Test
        void callsToActionAdsAndCreditsGo() {
            var recipe = read("""
                    Pancakes 🥞 SAVE this recipe for later!
                    Werbung
                    Recipe by @testkitchen
                    Photo: @testphoto

                    Ingredients:
                    200 g flour
                    2 eggs

                    Instructions:
                    Mix everything. Comment “PANCAKE” and I’ll DM you the full recipe!
                    Fry in a hot pan.
                    Follow @testkitchen for more 👇
                    Use code: TEST10 at checkout
                    """);

            assertEquals("Pancakes", recipe.title());
            assertEquals(List.of("Mix everything.", "Fry in a hot pan."), recipe.steps());
        }

        @Test
        void nutritionFactsGoButIngredientsNamedLikeNutrientsStay() {
            var recipe = read("""
                    Proteinbowl
                    All for 450 kcal!

                    Zutaten:
                    200 g Zucker
                    50 g Proteinpulver
                    522 Calories | 40g Protein | 30g Carbs

                    Nährwerte pro Portion:
                    450 kcal
                    30 g Protein
                    12 g Fett
                    """);

            assertEquals("Proteinbowl", recipe.title());
            assertEquals(List.of("200 g Zucker", "50 g Proteinpulver"), recipe.ingredientLines());
            assertTrue(recipe.steps().isEmpty());
        }

        @Test
        void invisibleCharactersAndOddLineBreaksAreNormalised() {
            var recipe = read("Brot" + LINE_SEPARATOR + "Zutaten:" + LINE_SEPARATOR + WORD_JOINER + "500" + NBSP
                    + "g Mehl\n" + INVISIBLE_SEPARATOR + "1 TL Salz\n" + BRAILLE_BLANK + "\n.\nTeig kneten und backen.\n");

            assertEquals("Brot", recipe.title());
            assertEquals(List.of("500 g Mehl", "1 TL Salz"), recipe.ingredientLines());
            assertEquals(List.of("Teig kneten und backen."), recipe.steps());
        }

        @Test
        void fancyFontsAreReadAsPlainLetters() {
            var recipe = read("𝐏𝐟𝐚𝐧𝐧𝐤𝐮𝐜𝐡𝐞𝐧\n𝐙𝐮𝐭𝐚𝐭𝐞𝐧:\n２ Eier\n");

            assertEquals("Pfannkuchen", recipe.title());
            assertEquals(List.of("2 Eier"), recipe.ingredientLines());
        }
    }

    @Nested
    class Titles {

        @Test
        void aTeaserAfterADashOrAnEmojiIsNoPartOfTheTitle() {
            assertEquals("Ofen-Gnocchi", read("Ofen-Gnocchi - unser Rezept der Woche\nZutaten:\n500 g Gnocchi\n").title());
            assertEquals("Ofen-Gnocchi", read("Ofen-Gnocchi 😍 so einfach und lecker\nZutaten:\n500 g Gnocchi\n").title());
        }

        @Test
        void aSeriesNameBeforeAColonGoesFromALongTitle() {
            var recipe = read("Sunday Brunch at Home: Shakshuka with Feta and Fresh Herbs\nIngredients:\n4 eggs\n");

            assertEquals("Shakshuka with Feta and Fresh Herbs", recipe.title());
        }

        @Test
        void aShoutedTitleIsWrittenNormally() {
            assertEquals("Lemon Bars", read("LEMON BARS 🍋\nIngredients:\n3 lemons\n").title());
        }

        @Test
        void aQuestionOrAStoryIsNoTitleTheLineAboveTheIngredientsMayBe() {
            var recipe = read("""
                    Smash or pass?

                    Crispy Chicken Wraps

                    Ingredients:
                    2 wraps
                    """);

            assertEquals("Crispy Chicken Wraps", recipe.title());
        }

        @Test
        void aLineAnnouncingTheRecipeIsNoTitle() {
            var recipe = read("""
                    Mein Opa hat dieses Rezept jeden Sonntag gekocht und wir lieben es bis heute sehr.

                    Hier kommt das Rezept 👇
                    Zutaten:
                    1 Gurke
                    """);

            assertNull(recipe.title());
        }
    }

    @Test
    void aListOfIdeasIsNoRecipe() {
        var text = cut.read("""
                5 Ideen für die Brotdose:

                1. Obstspieße: Kombiniere Trauben und Melone auf einem Spieß.
                2. Gemüsesticks: Schneide Karotten und Gurken in Sticks.
                3. Mini-Sandwiches: Belege Vollkornbrot mit Frischkäse.

                Habt ihr noch Ideen? Schreibt sie in die Kommentare!
                #brotdose #kinder
                """);

        assertFalse(text.isRecipe());
        assertNull(text.link());
    }

    @Test
    void aCaptionPointingToABlogIsNoRecipeButCarriesItsLink() {
        var text = cut.read("""
                Unser liebster Apfelkuchen, saftig und schnell gemacht!
                Das Rezept findet ihr auf dem Blog: https://example.com/apfelkuchen.
                #apfelkuchen #backen
                """);

        assertFalse(text.isRecipe());
        assertEquals("https://example.com/apfelkuchen", text.link());
    }

    @Test
    void aCaptionAskingForACommentIsNoRecipeAndHasNoLink() {
        var text = cut.read("Kommentiere REZEPT und ich schicke dir das Rezept per DM! 🍝\n#pasta");

        assertFalse(text.isRecipe());
        assertNull(text.link());
    }

    @Test
    void aRecipeKeepsTheLinkItWasTakenFrom() {
        var recipe = read("Zutaten:\n200 g Mehl\n\nTeig kneten.\n\nhttps://example.com/brot\n");

        assertEquals("https://example.com/brot", recipe.link());
        assertEquals(List.of("Teig kneten."), recipe.steps());
    }

    @Test
    void ofTwoRecipesTheFirstIsReadWithItsOwnTitle() {
        var recipe = read("""
                Zwei schnelle Frühstücke für jeden Tag!

                1. Overnight Oats
                Du brauchst: 50 g Haferflocken, 100 ml Milch, 1 TL Chiasamen
                So geht's: Alles mischen und über Nacht kühlen.

                2. Rührei
                Du brauchst: 2 Eier, 1 EL Butter, Salz
                So geht's: Eier verquirlen und in Butter stocken lassen.
                """);

        assertEquals("Overnight Oats", recipe.title());
        assertEquals(List.of("50 g Haferflocken", "100 ml Milch", "1 TL Chiasamen"), recipe.ingredientLines());
        assertEquals(List.of("Alles mischen und über Nacht kühlen."), recipe.steps());
    }

    @Test
    void textWithoutIngredientsIsNoRecipe() {
        assertFalse(cut.read("Heute gab es bei uns Pfannkuchen. Wie esst ihr sie am liebsten?").isRecipe());
        assertFalse(cut.read("").isRecipe());
    }
}
