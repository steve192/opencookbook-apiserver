import pytest

from cookpal_nutrition.sources import bls, fdc
from cookpal_nutrition.tables import read_table, write_table


class TestBlsValue:

    def test_a_number_is_read_as_published(self):
        assert bls.value("8.656") == 8.656

    @pytest.mark.parametrize("marker", ["<LOD", "<LOQ", "<LOD or <LOQ", "TR"])
    def test_an_amount_too_small_to_measure_is_zero(self, marker):
        assert bls.value(marker) == 0.0

    @pytest.mark.parametrize("marker", ["-", "", " "])
    def test_an_undetermined_value_is_unknown(self, marker):
        assert bls.value(marker) is None


class TestFdcFood:

    FOOD = {
        "fdcId": 170026,
        "dataType": "SR Legacy",
        "description": "Potatoes, flesh and skin, raw",
        "foodCategory": {"description": "Vegetables and Vegetable Products"},
        "nutrientConversionFactors": [
            {"type": ".CalorieConversionFactor", "proteinValue": 2.78},
            {"type": ".ProteinConversionFactor", "value": 6.25},
        ],
        "foodNutrients": [
            {"nutrient": {"id": 1003}, "amount": 2.05},
            {"nutrient": {"id": 1093}, "amount": 6},
            None,
            {"nutrient": None},
        ],
        "foodPortions": [
            {"amount": 1.0, "measureUnit": {"name": "undetermined"}, "modifier": "medium", "gramWeight": 213.0},
            {"amount": 1.0, "measureUnit": {"name": "cup"}, "modifier": "", "gramWeight": 150.0},
        ],
    }

    def test_nutrients_are_taken_by_id_and_missing_ones_are_empty(self):
        row = fdc.food_row(self.FOOD)

        assert row["protein"] == 2.05
        assert row["sodium_mg"] == 6.0
        assert row["fat"] is None

    def test_the_protein_conversion_factor_is_kept(self):
        assert fdc.food_row(self.FOOD)["protein_conversion_factor"] == 6.25

    def test_portions_keep_gram_weights_and_drop_the_undetermined_unit(self):
        portions = list(fdc.portion_rows(self.FOOD))

        assert portions[0] == {"fdc_id": 170026, "amount": 1.0, "measure_unit": None, "modifier": "medium",
                               "gram_weight": 213.0}
        assert portions[1]["measure_unit"] == "cup"
        assert portions[1]["modifier"] is None


def test_a_table_round_trips_floats_without_rounding(tmp_path):
    path = tmp_path / "table.csv"

    write_table(path, ("a", "b"), [{"a": 8.656, "b": None}])

    assert read_table(path) == [{"a": "8.656", "b": ""}]
