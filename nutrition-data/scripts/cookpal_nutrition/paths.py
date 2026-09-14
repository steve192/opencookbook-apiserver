"""Paths in nutrition-data/ and the apiserver. The newest raw download of each source wins."""

from pathlib import Path

NUTRITION_DATA = Path(__file__).resolve().parents[2]
REPOSITORY = NUTRITION_DATA.parent

RAW = NUTRITION_DATA / "raw"
SOURCES = NUTRITION_DATA / "sources"
CURATION = NUTRITION_DATA / "curation"
GOLD = NUTRITION_DATA / "gold"
LOCAL = NUTRITION_DATA / "local"

BLS_ARCHIVE_PATTERN = "BLS_*_DE.zip"
FDC_FOUNDATION_PATTERN = "FoodData_Central_foundation_food_json_*.zip"
FDC_SR_LEGACY_PATTERN = "FoodData_Central_sr_legacy_food_json_*.zip"

BLS_FOODS = SOURCES / "bls" / "foods.csv"
FDC_FOODS = SOURCES / "fdc" / "foods.csv"
FDC_PORTIONS = SOURCES / "fdc" / "portions.csv"
PROVENANCE = SOURCES / "provenance.json"

DATASET = REPOSITORY / "src" / "main" / "resources" / "nutrition"
BUILD_REPORT = LOCAL / "build-report.txt"


class MissingDownload(FileNotFoundError):
    pass


def newest_raw(pattern: str) -> Path:
    """The newest download matching a publisher's file name pattern. Release dates sort as text."""
    matches = sorted(RAW.glob(pattern))
    if not matches:
        raise MissingDownload(f"no file matching {pattern} in {RAW}; see SOURCES.md for where to get it")
    return matches[-1]
