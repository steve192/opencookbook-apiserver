import pytest

from cookpal_nutrition import catalogue, paths, reference
from cookpal_nutrition.curation import Curation, Families, FdcVocabulary, SourceInclusion
from cookpal_nutrition.sources import bls, fdc

REFERENCE = reference.load(paths.CURATION)


def bls_row(code, name_de, name_en, kcal=100.0):
    # Protein, carbohydrate and fat chosen so the recomputed energy matches the published one.
    row = {column: "0.0" for column in bls.COMPONENTS}
    row.update(code=code, name_de=name_de, name_en=name_en, ENERCC=str(kcal), PROT625=str(kcal / 4), CHO="0.0", FAT="0.0")
    return row


def fdc_row(fdc_id, description, kcal, data_type="Foundation", category="Vegetables"):
    row = {column: "" for column in fdc.FOOD_COLUMNS}
    row.update(fdc_id=str(fdc_id), data_type=data_type, category=category, description=description,
               protein=str(kcal / 4), fat="0", carbohydrate_by_difference="0")
    return row


def curation(**overrides) -> Curation:
    empty = SourceInclusion(frozenset(), frozenset(), frozenset())
    fields = dict(label="test", bls=SourceInclusion(frozenset({"X", "Y"}), frozenset(), frozenset()), fdc=empty,
                  families=Families(attach={}, bases=frozenset()), fdc_duplicates={},
                  fdc_vocabulary=FdcVocabulary(synonyms={}, implied={}, ignored_phrases=()), names={},
                  portions={}, properties={})
    fields.update(overrides)
    return Curation(**fields)


def build(bls_rows, fdc_rows=(), **curation_overrides):
    foods, report = catalogue.build(REFERENCE, curation(**curation_overrides),
                                    catalogue.Sources(list(bls_rows), list(fdc_rows), []))
    return {food.key: food for food in foods}, report


def test_preparations_in_a_family_are_variants_of_its_unprepared_food():
    foods, _ = build([bls_row("K110100", "Kartoffel geschält, roh", "Potato peeled, raw", 80),
                      bls_row("K110132", "Kartoffel geschält, gekocht", "Potato peeled, boiled", 72)])

    assert foods["bls-K110132"].variant_of == "bls-K110100"
    assert foods["bls-K110132"].states == ("BOILED",)
    assert foods["bls-K110100"].variant_of is None


def test_a_preparation_coded_under_another_prefix_is_linked_by_name():
    foods, report = build([bls_row("T204100", "Lachs roh", "Salmon raw", 180),
                           bls_row("T419152", "Lachs gedünstet", "Salmon stewed", 170)])

    assert foods["bls-T419152"].variant_of == "bls-T204100"
    assert report.lines["variant linked by name"]


def test_dishes_are_left_out():
    foods, _ = build([bls_row("X411243", "Hühnerbrühe", "Chicken stock", 8)])

    assert foods == {}


def test_a_prepared_food_nothing_explains_stays_a_food_of_its_own():
    foods, report = build([bls_row("V422072", "Geflügel Dönerfleisch, gegrillt", "Poultry kebab meat, grilled", 200),
                           bls_row("V422182", "Geflügel Dönerfleisch, gebraten", "Poultry kebab meat, fried", 210)])

    assert all(food.variant_of is None for food in foods.values())
    assert report.lines["prepared food without an unprepared base (kept as a food of its own)"]


def test_a_derived_name_two_foods_share_belongs_to_neither_and_the_description_steps_in():
    foods, report = build([bls_row("C213100", "Weizen Mehl", "Wheat flour", 350),
                           bls_row("C213200", "Weizen Mehl", "Wheat flour", 351)])

    names = {food.key: [name.text for name in food.names if name.language == "de"] for food in foods.values()}
    assert names == {"bls-C213100": ["Weizen Mehl"], "bls-C213200": ["Weizen Mehl (BLS C213200)"]}
    assert report.lines["derived name dropped"]
    assert report.lines["full source description used as name"]


def test_a_new_fdc_food_without_a_german_description_is_named_in_english_in_both_languages():
    foods, report = build([], [fdc_row(2710829, "Fonio, grain, dry, raw", 360)])

    food = foods["fdc-2710829"]
    assert {name.language for name in food.names} == {"de", "en"}
    assert report.lines["no de name, other language used"]


def test_curation_referring_to_codes_a_release_no_longer_has_is_reported_not_fatal():
    _, report = build([bls_row("F110100", "Apfel roh", "Apple raw", 58)],
                      families=Families(attach={"Z999932": "Z999900"}, bases=frozenset({"Z000000"})))

    assert report.lines["stale curation in families.yaml (attach) (ignored)"]
    assert report.lines["stale curation in families.yaml (bases) (ignored)"]


def test_curated_names_given_to_two_foods_stop_the_build():
    with pytest.raises(ValueError):
        build([bls_row("F110100", "Apfel roh", "Apple raw", 58), bls_row("F130100", "Birne roh", "Pear raw", 58)],
              names={"bls-F110100": {"de": ["Obst"], "en": ["Fruit"]}, "bls-F130100": {"de": ["Obst"], "en": ["Pear"]}})


def test_synonyms_are_added_after_a_foods_names_and_never_shown_first():
    foods, _ = build([bls_row("K110100", "Kartoffel geschält", "Potato peeled", 83)],
                     synonyms={"bls-K110100": {"de": ["Kartoffel", "Kartoffel geschält"], "en": ["Potato"]}})

    names = [(name.language, name.text, name.display) for name in foods["bls-K110100"].names]
    assert names == [("de", "Kartoffel geschält", True), ("de", "Kartoffel", False),
                     ("en", "Potato peeled", True), ("en", "Potato", False)]


def test_a_synonym_takes_its_name_from_the_food_it_would_otherwise_be_derived_for():
    foods, report = build([bls_row("M111300", "Vollmilch", "Whole milk", 62), bls_row("M150000", "Milch", "Milk", 40)],
                          synonyms={"bls-M111300": {"de": ["Milch"]}})

    assert [name.text for name in foods["bls-M111300"].names if name.language == "de"] == ["Vollmilch", "Milch"]
    assert "Milch" not in [name.text for name in foods["bls-M150000"].names if name.language == "de"]
    assert report.lines["derived name dropped"]


def test_synonyms_of_a_variant_are_reported_and_ignored():
    foods, report = build([bls_row("K110100", "Kartoffel roh", "Potato raw", 83),
                           bls_row("K110132", "Kartoffel gekocht", "Potato boiled", 80)],
                          synonyms={"bls-K110132": {"de": ["Salzkartoffel"]}})

    assert foods["bls-K110132"].variant_of == "bls-K110100"
    assert report.lines["synonyms of a variant ignored"]


def test_sr_legacy_foods_are_included_only_from_the_groups_bls_and_foundation_foods_lack_or_by_id():
    foods, _ = build([], [fdc_row(171329, "Spices, cinnamon, ground", 247, "SR Legacy", "Spices and Herbs"),
                          fdc_row(170393, "Carrots, raw", 41, "SR Legacy", "Vegetables and Vegetable Products"),
                          fdc_row(175042, "Leavening agents, yeast, baker's, compressed", 105, "SR Legacy", "Baked Products")],
                     fdc=SourceInclusion(frozenset(), frozenset({"175042"}), frozenset(), frozenset({"Spices and Herbs"})))

    assert set(foods) == {"fdc-171329", "fdc-175042"}


def test_a_synonym_of_the_food_that_derives_the_same_name_takes_nothing_from_it():
    foods, report = build([bls_row("G312100", "Broccoli roh", "Broccoli raw", 35)],
                          synonyms={"bls-G312100": {"de": ["Broccoli", "Brokkoli"]}})

    assert [name.text for name in foods["bls-G312100"].names if name.language == "de"] == ["Broccoli", "Brokkoli"]
    assert not report.lines.get("derived name dropped")


def test_names_alike_but_for_their_numbers_are_reported_where_the_foods_differ_in_energy():
    _, report = build([bls_row("M402400", "Gouda 30 % Fett", "Gouda 30 % fat", 265),
                       bls_row("M402600", "Gouda 48 % Fett", "Gouda 48 % fat", 379),
                       bls_row("B256000", "Weizenmischbrot mit Ölsamen", "Wheat-rye bread (> 50 % wheat) with oilseeds", 250),
                       bls_row("B276000", "Roggenmischbrot mit Ölsamen", "Rye-wheat bread (> 50 % rye) with oilseeds", 240)])

    assert report.lines["names alike to the matcher, differing in energy"] == [
        "'fett gouda' (de): bls-M402400 265 kcal, bls-M402600 379 kcal", "'fat gouda' (en): bls-M402400 265 kcal, bls-M402600 379 kcal"]


def test_spices_and_foods_without_energy_are_negligible_other_rich_foods_are_not():
    foods, _ = build([bls_row("R258100", "Pfeffer schwarz, getrocknet", "Pepper black, dried", 304),
                      bls_row("R111000", "Speisesalz", "Salt", 0),
                      bls_row("R132000", "Senf mittelscharf", "Mustard", 88)],
                     [fdc_row(171329, "Spices, paprika", 282, "SR Legacy", "Spices and Herbs")],
                     fdc=SourceInclusion(frozenset(), frozenset(), frozenset(), frozenset({"Spices and Herbs"})))

    assert {key for key, food in foods.items() if food.negligible} == {"bls-R258100", "bls-R111000", "fdc-171329"}
