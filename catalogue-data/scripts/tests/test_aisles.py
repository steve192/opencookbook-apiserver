import pytest

from cookpal_catalogue import non_food, paths, reference
from cookpal_catalogue.catalogue import aisles, shopping_tiles
from cookpal_catalogue.catalogue.model import Food, Name, Report, UndecidedFoods
from cookpal_catalogue.curation import Aisles, Curation, InvalidCuration, NonFood
from cookpal_catalogue.nutrients import Nutrients

NUTRIENTS = Nutrients(100.0, 420.0, 1.0, 0.0, 10.0, 0.0, 0.0, 5.0, 0.0)
REFERENCE = reference.load(paths.CURATION)


def food(code, name, variant_of=None, states=(), source_type="BLS"):
    return Food(key=f"{source_type.lower()}-{code}", source_type=source_type, source_code=code, source_name=name,
                source_names={"de": name}, states=states, nutrients=NUTRIENTS, variant_of=variant_of)


def curation(overrides=None, tiles=None, items=()):
    return Curation(
        label="test", bls=None, fdc=None, families=None, fdc_duplicates={}, fdc_vocabulary=None, names={},
        portions={}, diet_classes=None,
        aisles=Aisles(subgroup_defaults={"G5": "FRUIT_VEG", "S1": "SWEETS_SNACKS"},
                      state_defaults={"CANNED": "CANNED_JARS"},
                      source_name_patterns={"FROZEN": "tiefgefroren"},
                      overrides=overrides or {}),
        shopping_tiles=tiles or {}, non_food=tuple(items))


def assign(foods, overrides=None):
    by_key = {item.key: item for item in foods}
    aisles.assign(curation(overrides), by_key, Report())
    return by_key


class TestAisles:

    def test_the_subgroup_places_a_food_nobody_curated(self):
        assert assign([food("G561100", "Tomate")])["bls-G561100"].aisle == "FRUIT_VEG"

    def test_a_state_moves_a_variant_away_from_its_base(self):
        placed = assign([food("G561100", "Tomate"),
                         food("G568902", "Tomate, Konserve", variant_of="bls-G561100", states=("CANNED",))])

        assert placed["bls-G568902"].aisle == "CANNED_JARS"

    def test_a_word_in_the_source_name_says_how_it_is_sold(self):
        assert assign([food("G570200", "Zuckermais tiefgefroren")])["bls-G570200"].aisle == "FROZEN"

    def test_a_variant_takes_its_base_aisle(self):
        placed = assign([food("G561100", "Tomate"), food("G561132", "Tomate gekocht", variant_of="bls-G561100")])

        assert placed["bls-G561132"].aisle == "FRUIT_VEG"

    def test_an_override_wins_over_everything(self):
        placed = assign([food("S111000", "Zucker weiß")], {"bls-S111000": "BAKING"})

        assert placed["bls-S111000"].aisle == "BAKING"

    def test_a_food_nobody_placed_stops_the_build(self):
        with pytest.raises(UndecidedFoods, match="no aisle"):
            assign([food("171610", "Sauce, worcestershire", source_type="FDC")])


class TestShoppingTiles:

    def test_a_listed_base_becomes_a_tile_with_its_icon(self):
        tomato = food("G561100", "Tomate")
        shopping_tiles.assign(REFERENCE, curation(tiles={tomato.key: "tomato"}), {tomato.key: tomato}, Report())

        assert tomato.shopping_tile and tomato.icon == "tomato"

    def test_an_icon_fluent_does_not_have_is_refused(self):
        tomato = food("G561100", "Tomate")
        with pytest.raises(InvalidCuration, match="not in fluent-icons"):
            shopping_tiles.assign(REFERENCE, curation(tiles={tomato.key: "tomatoes"}), {tomato.key: tomato}, Report())

    def test_a_variant_cannot_be_a_tile(self):
        variant = food("G561132", "Tomate gekocht", variant_of="bls-G561100")
        with pytest.raises(InvalidCuration, match="variant"):
            shopping_tiles.assign(REFERENCE, curation(tiles={variant.key: None}), {variant.key: variant}, Report())


class TestNonFood:

    def test_a_name_a_food_already_has_is_refused(self):
        tomato = food("G561100", "Tomate")
        tomato.names.append(Name("de", "Tomate", True))
        clash = NonFood(key="tomato-soap", aisle="DRUGSTORE", icon=None, names={"de": ["tomate"], "en": ["soap"]})

        with pytest.raises(InvalidCuration, match="already a name of bls-G561100"):
            non_food.check(REFERENCE, curation(items=[clash]), [tomato])

    def test_items_are_shipped_in_key_order(self):
        items = [NonFood("soap", "DRUGSTORE", "soap", {"de": ["Seife"], "en": ["soap"]}),
                 NonFood("foil", "HOUSEHOLD", None, {"de": ["Alufolie"], "en": ["foil"]})]

        assert [item.key for item in non_food.check(REFERENCE, curation(items=items), [])] == ["foil", "soap"]
