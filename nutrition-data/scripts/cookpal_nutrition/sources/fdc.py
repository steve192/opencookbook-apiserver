"""USDA FoodData Central: Foundation Foods and SR Legacy JSON. Values are extracted unchanged."""

import json
import re
import zipfile
from pathlib import Path
from typing import Iterable, Iterator

# FDC nutrient ids, by the column they are written to.
NUTRIENTS = {
    "nitrogen": 1002,
    "protein": 1003,
    "fat": 1004,
    "carbohydrate_by_difference": 1005,
    "carbohydrate_by_summation": 1050,
    "fibre": 1079,
    "sugars_total_nlea": 2000,
    "sugars_total": 1063,
    "alcohol": 1018,
    "sodium_mg": 1093,
    "saturated_fat": 1258,
    "energy_kcal": 1008,
}

FOOD_COLUMNS = ("fdc_id", "data_type", "category", "description", "protein_conversion_factor") + tuple(NUTRIENTS)
PORTION_COLUMNS = ("fdc_id", "amount", "measure_unit", "modifier", "gram_weight")

UNDETERMINED_UNIT = "undetermined"
# The data type and release in a download name, e.g. FoodData_Central_foundation_food_json_2026-04-30.zip.
ARCHIVE_RELEASE = re.compile(r"FoodData_Central_(\w+?)_food_json_([\d-]+)\.zip$")


def release(archive: Path) -> str:
    """The data type and release of a download: "foundation 2026-04-30"."""
    match = ARCHIVE_RELEASE.search(archive.name)
    return f"{match.group(1).replace('_', ' ')} {match.group(2)}" if match else archive.stem


def read_foods(archives: Iterable[Path]) -> Iterator[dict]:
    """Every food of every archive. Some downloads contain null entries; those are skipped."""
    for archive in archives:
        for food in _foods_in(archive):
            if food:
                yield food


def food_row(food: dict) -> dict[str, object]:
    values = {nutrient_id: entry.get("amount")
              for entry in food.get("foodNutrients") or []
              if entry and entry.get("nutrient")
              for nutrient_id in [entry["nutrient"]["id"]]}
    row: dict[str, object] = {
        "fdc_id": food["fdcId"],
        "data_type": food["dataType"],
        "category": (food.get("foodCategory") or {}).get("description"),
        "description": food["description"],
        "protein_conversion_factor": _protein_conversion_factor(food),
    }
    for column, nutrient_id in NUTRIENTS.items():
        value = values.get(nutrient_id)
        row[column] = float(value) if value is not None else None
    return row


def portion_rows(food: dict) -> Iterator[dict[str, object]]:
    for portion in food.get("foodPortions") or []:
        unit = (portion.get("measureUnit") or {}).get("name")
        yield {
            "fdc_id": food["fdcId"],
            "amount": _float(portion.get("amount")),
            "measure_unit": None if unit == UNDETERMINED_UNIT else unit,
            "modifier": portion.get("modifier") or None,
            "gram_weight": _float(portion.get("gramWeight")),
        }


def _foods_in(archive: Path) -> list:
    with zipfile.ZipFile(archive) as zipped:
        names = [name for name in zipped.namelist() if name.endswith(".json")]
        if len(names) != 1:
            raise ValueError(f"expected one JSON file in {archive}, found {names}")
        document = json.loads(zipped.read(names[0]))
    if len(document) != 1:
        raise ValueError(f"expected one top-level list in {archive}, found {list(document)}")
    return next(iter(document.values()))


def _protein_conversion_factor(food: dict) -> float | None:
    for factor in food.get("nutrientConversionFactors") or []:
        if factor and factor.get("type") == ".ProteinConversionFactor" and factor.get("value") is not None:
            return float(factor["value"])
    return None


def _float(value) -> float | None:
    return float(value) if value is not None else None
