import shutil

import pytest

from cookpal_catalogue import curation, dataset, feature_facts, paths
from cookpal_catalogue.curation import InvalidCuration, Season, Storage
from tests.test_catalogue import bls_row, build
from tests.test_catalogue import curation as curation_with


@pytest.fixture
def curation_dir(tmp_path):
    target = tmp_path / "curation"
    shutil.copytree(paths.CURATION, target)
    return target


def load_with(curation_dir, file, content):
    (curation_dir / file).write_text(content, encoding="utf-8")
    return curation.load(curation_dir)


class TestShippedCuration:

    def test_loads(self):
        seasons = curation.load(paths.CURATION).seasons["DE"]
        assert seasons["bls-G450100"].months[5] == ("FIELD", "PROTECTED")


class TestSeasonCuration:

    def test_a_month_outside_the_year_is_refused(self, curation_dir):
        with pytest.raises(InvalidCuration, match="month 13"):
            load_with(curation_dir, "seasons-de.yaml", "foods:\n  bls-X:\n    category: FRUIT\n    months: {13: [FIELD]}\n")

    def test_an_unknown_growing_method_is_refused(self, curation_dir):
        with pytest.raises(InvalidCuration, match="not one of FIELD"):
            load_with(curation_dir, "seasons-de.yaml", "foods:\n  bls-X:\n    category: FRUIT\n    months: {6: [ORCHARD]}\n")

    def test_the_region_comes_from_the_file_name(self, curation_dir):
        loaded = load_with(curation_dir, "seasons-at.yaml", "foods:\n  bls-X:\n    category: SALAD\n    months: {6: [FIELD]}\n")
        assert loaded.seasons["AT"]["bls-X"] == Season(category="SALAD", months={6: ("FIELD",)})


class TestStorageAndSoakingCuration:

    def test_a_storage_place_must_be_known(self, curation_dir):
        with pytest.raises(InvalidCuration, match="not one of FRIDGE, COOL_DARK"):
            load_with(curation_dir, "storage.yaml", "foods:\n  bls-X: {place: SHELF}\n")

    def test_a_food_must_soak_up_some_water(self, curation_dir):
        with pytest.raises(InvalidCuration, match="must soak up some water"):
            load_with(curation_dir, "soaking.yaml", "foods:\n  bls-X: {absorption: 0}\n")


ASPARAGUS = bls_row("G450100", "Spargel roh", "Asparagus raw")
RICE = bls_row("C352000", "Reis poliert", "Rice polished")
SPRING = Season(category="VEGETABLE", months={5: ("FIELD",), 4: ("PROTECTED", "FIELD")})


def facts_of(bls_rows, **curation_overrides):
    curated = curation_with(**curation_overrides)
    foods, report = build(bls_rows, **curation_overrides)
    return feature_facts.build(curated, list(foods.values()), report)


class TestFeatureFacts:

    def test_seasonal_produce_ships_in_a_file_of_its_own(self):
        storage = Storage(place="FRIDGE", cold_sensitive=False, ethylene_producer=False, ethylene_sensitive=True)
        facts = facts_of([ASPARAGUS], seasons={"DE": {"bls-G450100": SPRING}}, storage={"bls-G450100": storage})

        assert [dataset._seasonal(produce) for produce in facts.seasonal] == [{
            "food": "bls-G450100",
            "category": "VEGETABLE",
            "seasons": {"DE": {"4": ["PROTECTED", "FIELD"], "5": ["FIELD"]}},
            "storage": {"place": "FRIDGE", "ethyleneSensitive": True},
        }]

    def test_soaking_ships_in_a_file_of_its_own(self):
        assert facts_of([ASPARAGUS], soaking={"bls-G450100": 1.5}).soaking == {"bls-G450100": 1.5}

    def test_a_tile_shows_the_first_everyday_synonym(self):
        facts = facts_of([RICE], shopping_tiles=frozenset({"bls-C352000"}),
                         synonyms={"bls-C352000": {"de": ["Reis", "Milchreis"], "en": ["rice"]}})

        assert facts.shopping_tiles == {"bls-C352000": {"de": "Reis", "en": "Rice"}}

    def test_a_tile_without_synonyms_shows_the_display_name(self):
        facts = facts_of([RICE], shopping_tiles=frozenset({"bls-C352000"}))

        assert facts.shopping_tiles == {"bls-C352000": {"de": "Reis poliert", "en": "Rice polished"}}

    def test_the_food_record_carries_no_feature_facts(self):
        foods, _ = build([ASPARAGUS], seasons={"DE": {"bls-G450100": SPRING}}, soaking={"bls-G450100": 1.5},
                         shopping_tiles=frozenset({"bls-G450100"}))

        assert not ({"seasonCategory", "seasons", "storage", "soakAbsorption", "shoppingTile"}
                    & dataset._food(foods["bls-G450100"]).keys())

    def test_regions_must_agree_on_the_category(self):
        with pytest.raises(InvalidCuration, match="in another region"):
            facts_of([ASPARAGUS], seasons={"AT": {"bls-G450100": Season(category="SALAD", months={5: ("FIELD",)})},
                                           "DE": {"bls-G450100": SPRING}})

    def test_storage_needs_a_season(self):
        storage = Storage(place="FRIDGE", cold_sensitive=False, ethylene_producer=False, ethylene_sensitive=False)
        with pytest.raises(InvalidCuration, match="have no season"):
            facts_of([ASPARAGUS], storage={"bls-G450100": storage})

    @pytest.mark.parametrize("facts", [{"soaking": {"bls-G450132": 1.0}},
                                       {"shopping_tiles": frozenset({"bls-G450132"})}])
    def test_a_variant_is_refused(self, facts):
        with pytest.raises(InvalidCuration, match="variant"):
            facts_of([ASPARAGUS, bls_row("G450132", "Spargel gekocht", "Asparagus boiled")], **facts)

    def test_a_fact_for_a_food_the_catalogue_lacks_is_reported_stale(self):
        report = build([ASPARAGUS])[1]
        feature_facts.build(curation_with(soaking={"bls-X": 1.0}), [], report)

        assert any("soaking.yaml" in topic for topic in report.lines)
