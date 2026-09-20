import pytest

from cookpal_nutrition.catalogue import diets
from cookpal_nutrition.catalogue.model import Food, Report, UndecidedFoods
from cookpal_nutrition.curation import Curation, DietClasses
from cookpal_nutrition.nutrients import Nutrients

NUTRIENTS = Nutrients(100.0, 420.0, 1.0, 0.0, 10.0, 0.0, 0.0, 5.0, 0.0)
GROUP_DEFAULTS = {"U": "MEAT", "M": "VEGETARIAN", "G": "VEGAN", "H": "VEGAN"}
REVIEW_PATTERNS = {"MEAT": "wurst|speck", "VEGETARIAN": "butter|milch"}


def food(code, name, variant_of=None, source_type="BLS"):
    return Food(key=f"{source_type.lower()}-{code}", source_type=source_type, source_code=code, source_name=name,
                source_names={"de": name}, states=(), nutrients=NUTRIENTS, variant_of=variant_of)


def assign(foods, overrides=None):
    """Runs the step over the given foods, keyed the way the build keys them."""
    curation = Curation(
        label="test", bls=None, fdc=None, families=None, fdc_duplicates={}, fdc_vocabulary=None,
        names={}, portions={},
        diet_classes=DietClasses(group_defaults=GROUP_DEFAULTS, review_patterns=REVIEW_PATTERNS,
                                 overrides=overrides or {}),
    )
    by_key = {item.key: item for item in foods}
    diets.assign(curation, by_key, Report())
    return by_key


class TestGroupDecides:

    def test_the_group_letter_classifies_a_food_nobody_curated(self):
        assigned = assign([food("U100000", "Rind Hackfleisch"), food("G200000", "Tomate")])

        assert assigned["bls-U100000"].diet_class == "MEAT"
        assert assigned["bls-G200000"].diet_class == "VEGAN"

    def test_a_group_without_a_default_has_to_be_curated(self):
        with pytest.raises(UndecidedFoods):
            assign([food("Q120000", "Olivenöl")])

    def test_an_fdc_food_has_no_group_and_has_to_be_curated(self):
        with pytest.raises(UndecidedFoods):
            assign([food("171610", "Sauce, worcestershire", source_type="FDC")])


class TestNamesOnlyNominate:
    """A pattern may raise a question; only an override answers one."""

    def test_a_name_disagreeing_with_its_group_stops_the_build(self):
        with pytest.raises(UndecidedFoods, match="no reviewed diet class"):
            assign([food("G300000", "Gemüse mit Butter")])

    def test_an_override_answers_the_question(self):
        assigned = assign([food("G300000", "Gemüse mit Butter")], {"bls-G300000": "VEGETARIAN"})

        assert assigned["bls-G300000"].diet_class == "VEGETARIAN"

    def test_an_override_may_reject_what_the_pattern_read_into_the_name(self):
        # "Sojawürstchen" matches the meat pattern and is a plant food regardless
        assigned = assign([food("H014900", "Sojawürstchen")], {"bls-H014900": "VEGAN"})

        assert assigned["bls-H014900"].diet_class == "VEGAN"

    def test_a_plant_word_in_an_animal_group_says_nothing(self):
        # Only a stricter reading is worth a question; a sausage stays a sausage
        assigned = assign([food("U200000", "Rind Hackfleisch mit Butter")])

        assert assigned["bls-U200000"].diet_class == "MEAT"


class TestVariants:

    def test_a_variant_takes_the_class_of_its_base(self):
        assigned = assign([food("U100000", "Rind Hackfleisch"), food("U100032", "Rind Hackfleisch gekocht", "bls-U100000")])

        assert assigned["bls-U100032"].diet_class == "MEAT"

    def test_a_variant_is_never_nominated_for_its_own_name(self):
        # Its name carries the same words as its base's, so asking twice would only double the work
        assigned = assign([food("G200000", "Gemüse"), food("G200032", "Gemüse mit Butter", "bls-G200000")],
                          {"bls-G200000": "VEGAN"})

        assert assigned["bls-G200032"].diet_class == "VEGAN"

    def test_a_variant_may_still_be_curated_apart_from_its_base(self):
        assigned = assign([food("G200000", "Gemüse"), food("G200032", "Gemüse in Butter geschwenkt", "bls-G200000")],
                          {"bls-G200032": "VEGETARIAN"})

        assert assigned["bls-G200000"].diet_class == "VEGAN"
        assert assigned["bls-G200032"].diet_class == "VEGETARIAN"


class TestReporting:

    def test_the_report_names_every_open_question(self):
        with pytest.raises(UndecidedFoods) as raised:
            assign([food("G300000", "Gemüse mit Butter"), food("Q120000", "Olivenöl")])

        lines = raised.value.report.lines
        assert any("G300000" in line for line in lines[diets.NOMINATED])
        assert any("Q120000" in line for line in lines[diets.UNDECIDED])
