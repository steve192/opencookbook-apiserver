from pathlib import Path

import pytest

from cookpal_nutrition import fdc_duplicates, names, nutrients, reference
from cookpal_nutrition.sources import bls, fdc

PREPARATION = {"roh", "gekocht", "gebraten", "raw", "boiled", "fried"}


class TestDerivedNames:

    def test_preparation_words_are_taken_out(self):
        assert names.derive("Kartoffel geschält, roh", PREPARATION) == ["Kartoffel geschält"]

    def test_inflected_preparation_words_are_taken_out(self):
        assert names.derive("gekochte Kartoffeln", PREPARATION) == ["Kartoffeln"]

    def test_a_word_merely_starting_like_a_preparation_word_stays(self):
        assert names.derive("Rohrzucker", PREPARATION) == ["Rohrzucker"]

    def test_leading_slash_alternatives_become_names_of_their_own(self):
        assert names.derive("Dorsch/Kabeljau, roh", PREPARATION) == ["Dorsch", "Kabeljau"]

    def test_multi_word_alternatives_are_split_as_phrases(self):
        assert names.derive("White bread/wheat bread", PREPARATION) == ["White bread", "wheat bread"]

    def test_slashes_inside_brackets_describe_and_are_kept(self):
        assert names.derive("Schwein Kotelett (Rücken/cranial) roh", PREPARATION) == ["Schwein Kotelett (Rücken/cranial)"]

    def test_a_bracketed_word_sharing_a_words_start_or_end_is_a_synonym(self):
        assert names.derive("Lachs geräuchert (Räucherlachs)", PREPARATION) == ["Lachs geräuchert (Räucherlachs)", "Räucherlachs"]
        assert names.derive("Kartoffelstärke (Kartoffelmehl)", PREPARATION) == ["Kartoffelstärke (Kartoffelmehl)", "Kartoffelmehl"]

    def test_bracketed_descriptions_are_no_synonyms(self):
        assert names.derive("Butterkekse (Mürbeteig)", PREPARATION) == ["Butterkekse (Mürbeteig)"]
        assert names.derive("Schwein Schnitzel (Oberschale)", PREPARATION) == ["Schwein Schnitzel (Oberschale)"]

    def test_context_phrases_are_taken_out_only_when_given(self):
        assert names.derive("Lachs gebraten ohne Fett (Pfanne)", PREPARATION, {"ohne Fett", "Pfanne"}) == ["Lachs"]
        assert names.derive("Schwein Bug ohne Fett, roh", PREPARATION) == ["Schwein Bug ohne Fett"]


class TestHarmonisedNutrients:

    FDC = {"fdc_id": "1", "nitrogen": "", "protein": "20", "protein_conversion_factor": "5.7", "fat": "10",
           "carbohydrate_by_difference": "30", "carbohydrate_by_summation": "", "fibre": "5", "alcohol": "",
           "sodium_mg": "400", "saturated_fat": "2", "sugars_total_nlea": "3", "sugars_total": ""}

    def test_fdc_carbohydrates_by_difference_lose_their_fibre(self):
        assert nutrients.from_fdc(self.FDC).carbohydrates == 25

    def test_fdc_protein_is_brought_to_nitrogen_times_six_and_a_quarter(self):
        assert nutrients.from_fdc(self.FDC).protein == pytest.approx(20 / 5.7 * 6.25, abs=0.001)

    def test_fdc_salt_comes_from_sodium(self):
        assert nutrients.from_fdc(self.FDC).salt == 1.0

    def test_energy_uses_the_eu_factors(self):
        protein = 20 / 5.7 * 6.25
        assert nutrients.from_fdc(self.FDC).energy_kcal == pytest.approx(4 * protein + 4 * 25 + 9 * 10 + 2 * 5, abs=0.1)

    def test_a_part_never_exceeds_its_whole(self):
        harmonised = nutrients.from_fdc({**self.FDC, "fat": "0.07", "saturated_fat": "0.116", "sugars_total_nlea": "40"})
        assert (harmonised.saturated_fat, harmonised.sugar) == (0.07, 25)

    def test_a_food_without_fat_cannot_have_its_energy_computed(self):
        with pytest.raises(nutrients.IncompleteNutrients):
            nutrients.from_fdc({**self.FDC, "fat": ""})


class TestFdcDuplicateRule:

    VOCABULARY = fdc_duplicates.Vocabulary({"eggplant": "aubergine"}, {"egg": "chicken"}, ["year round average"])

    def candidates(self, *foods):
        return fdc_duplicates.CandidateIndex(self.VOCABULARY.candidate(code, name, kcal) for code, name, kcal in foods)

    def decide(self, description, kcal, *foods):
        return fdc_duplicates.decide(self.VOCABULARY.describe(description, kcal), self.candidates(*foods))

    def test_a_variety_duplicates_the_food(self):
        assert self.decide("Apples, fuji, with skin, raw", 64, ("F110100", "Apple raw", 58)).duplicate_of == "F110100"

    def test_american_words_are_read_as_british_ones(self):
        assert self.decide("Eggplant, raw", 21, ("G510100", "Aubergine raw", 19)).duplicate_of == "G510100"

    def test_a_category_first_description_is_recognised(self):
        assert self.decide("Fish, haddock, raw", 69, ("T206100", "Haddock raw", 74)).duplicate_of == "T206100"

    def test_different_energy_makes_a_new_food(self):
        assert self.decide("Egg, white, dried", 350, ("E113100", "Chicken egg white, raw", 42)).duplicate_of is None

    def test_one_shared_generic_word_is_not_enough(self):
        assert self.decide("Yogurt, Greek, plain, whole milk", 93, ("M111300", "Whole milk, fresh, 3.5 % fat", 64)).duplicate_of is None

    def test_a_candidate_in_the_same_state_wins(self):
        decision = self.decide("Onions, raw", 39, ("G480200", "Onion deep-frozen", 37), ("G480100", "Onion raw", 34))
        assert decision.duplicate_of == "G480100"

    def test_an_implied_word_tells_candidates_apart(self):
        decision = self.decide("Egg, whole, raw, fresh", 139, ("E106100", "Quail egg raw", 135), ("E111100", "Chicken egg raw", 135))
        assert decision.duplicate_of == "E111100"

    def test_ignored_phrases_do_not_count_as_naming_words(self):
        assert self.decide("Tomatoes, red, ripe, raw, year round average", 18, ("G561100", "Tomato red, raw", 22)).duplicate_of == "G561100"

    def test_portions_may_carry_over_despite_differing_energy(self):
        garlic = self.VOCABULARY.describe("Garlic, raw", 149)
        index = self.candidates(("G490100", "Garlic raw", 97))
        assert fdc_duplicates.decide(garlic, index).duplicate_of is None
        assert fdc_duplicates.decide(garlic, index, fdc_duplicates.SAME_KIND).duplicate_of == "G490100"

    def test_plural_qualifiers_are_not_naming_words(self):
        assert self.decide("Nuts, pecans, halves, raw", 746, ("H160100", "Pecan nut", 723)).duplicate_of == "H160100"

    def test_the_most_precise_candidate_wins(self):
        decision = self.decide("Cabbage, red, raw", 34, ("G342100", "White cabbage raw", 32), ("G341100", "Red cabbage raw", 28))
        assert decision.duplicate_of == "G341100"


class TestReleases:

    def test_bls_release_is_read_from_the_archive_name(self):
        assert bls.release(Path("BLS_4_0_2025_DE.zip")) == "4.0 (2025)"

    def test_fdc_release_is_read_from_the_download_name(self):
        assert fdc.release(Path("FoodData_Central_foundation_food_json_2026-04-30.zip")) == "foundation 2026-04-30"


class TestUnits:

    def test_a_container_may_state_what_it_typically_holds(self):
        units = reference._units({"can": {"kind": "COUNT", "typicalGrams": 400},
                                  "bottle": {"kind": "COUNT", "typicalMillilitres": 700}})
        assert (units["can"].typical_grams, units["bottle"].typical_millilitres) == (400, 700)

    @pytest.mark.parametrize("spec", [
        {"can": {"kind": "MASS", "grams": 1, "typicalGrams": 400}},
        {"can": {"kind": "COUNT", "typicalGrams": 400, "typicalMillilitres": 400}},
        {"can": {"kind": "COUNT"}, "can-small": {"kind": "COUNT", "sizeOf": "can", "factor": 0.5, "typicalGrams": 200}},
    ])
    def test_a_typical_size_belongs_to_a_plain_count_unit_in_one_measure(self, spec):
        with pytest.raises(reference.InvalidReference):
            reference._units(spec)
