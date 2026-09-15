"""FDC foods that duplicate no BLS food become catalogue foods.

Candidates: Foundation Foods, SR Legacy foods of categories in inclusion.yaml, and single includes.
curation/fdc-duplicates.yaml corrects the duplicate rule per food; the rule's own decisions are reported.
"""

from cookpal_nutrition import fdc_duplicates
from cookpal_nutrition import nutrients as nutrient_values
from cookpal_nutrition.catalogue.model import Food, Report, bls_key, fdc_key
from cookpal_nutrition.catalogue.words import StateWords
from cookpal_nutrition.curation import FDC_NEW, Curation
from cookpal_nutrition.nutrients import IncompleteNutrients
from cookpal_nutrition.sources import bls

FOUNDATION = "Foundation"
SR_LEGACY = "SR Legacy"
SOURCE_LANGUAGE = "en"


def build(curation: Curation, rows: list[dict[str, str]], bls_rows: list[dict[str, str]], bls_foods: dict[str, Food],
          words: StateWords, report: Report) -> dict[str, Food]:
    included = {int(row["fdc_id"]): row for row in rows if _included(row, curation)}
    report.stale("inclusion.yaml (fdc)", (curation.fdc.include | curation.fdc.exclude) - {str(row["fdc_id"]) for row in rows})
    report.stale("inclusion.yaml (fdc srLegacyCategories)",
                 curation.fdc.sr_legacy_groups - {row["category"] for row in rows if row["data_type"] == SR_LEGACY})
    report.stale("fdc-duplicates.yaml", curation.fdc_duplicates.keys() - included.keys())

    vocabulary = vocabulary_of(curation)
    candidates = bls_candidates(vocabulary, bls_rows, bls_foods)

    foods: dict[str, Food] = {}
    for fdc_id, row in sorted(included.items()):
        try:
            food_nutrients = nutrient_values.from_fdc(row)
        except IncompleteNutrients as incomplete:
            report.add("excluded, incomplete nutrients", f"FDC {incomplete}: {row['description']}")
            continue
        if _duplicate_of(fdc_id, row, food_nutrients.energy_kcal, curation, vocabulary, candidates, bls_foods, report):
            continue
        description = row["description"]
        states = words.processings_named(description, SOURCE_LANGUAGE) | words.preparations_named(description, SOURCE_LANGUAGE)
        foods[fdc_key(fdc_id)] = Food(key=fdc_key(fdc_id), source_type="FDC", source_code=str(fdc_id),
                                      source_name=description, source_names={SOURCE_LANGUAGE: description},
                                      states=tuple(sorted(states)), nutrients=food_nutrients)
    for data_type in sorted({row["data_type"] for row in included.values()}):
        new = sum(1 for key in foods if included[int(foods[key].source_code)]["data_type"] == data_type)
        considered = sum(1 for row in included.values() if row["data_type"] == data_type)
        report.add("summary", f"FDC {data_type}: {considered} included, {new} new foods")
    return foods


def vocabulary_of(curation: Curation) -> fdc_duplicates.Vocabulary:
    words = curation.fdc_vocabulary
    return fdc_duplicates.Vocabulary(words.synonyms, words.implied, words.ignored_phrases)


def bls_candidates(vocabulary: fdc_duplicates.Vocabulary, bls_rows: list[dict[str, str]],
                   foods: dict[str, Food]) -> fdc_duplicates.CandidateIndex:
    """Every BLS catalogue food as a duplicate candidate, described in English."""
    return fdc_duplicates.CandidateIndex(vocabulary.candidate(bls_key(row["code"]), row["name_en"], bls.value(row["ENERCC"]) or 0.0)
                                         for row in bls_rows if bls_key(row["code"]) in foods)


def _included(row: dict[str, str], curation: Curation) -> bool:
    fdc_id = str(row["fdc_id"])
    if fdc_id in curation.fdc.exclude:
        return False
    if fdc_id in curation.fdc.include:
        return True
    if row["data_type"] == FOUNDATION:
        return row["category"] not in curation.fdc.excluded_groups
    return row["data_type"] == SR_LEGACY and row["category"] in curation.fdc.sr_legacy_groups


def _duplicate_of(fdc_id: int, row: dict[str, str], energy_kcal: float, curation: Curation,
                  vocabulary: fdc_duplicates.Vocabulary, candidates: fdc_duplicates.CandidateIndex,
                  bls_foods: dict[str, Food], report: Report) -> str | None:
    """The key of the BLS food this FDC food duplicates, or None if it is a new food."""
    corrected = curation.fdc_duplicates.get(fdc_id)
    if corrected == FDC_NEW:
        return None
    if corrected is not None:
        if bls_key(corrected) in bls_foods:
            return bls_key(corrected)
        report.stale("fdc-duplicates.yaml", [f"{fdc_id}: {corrected} (no such catalogue food; decided by rule)"])
    decision = fdc_duplicates.decide(vocabulary.describe(row["description"], energy_kcal), candidates)
    outcome = f"duplicates {decision.duplicate_of}" if decision.duplicate_of else "new"
    report.add("FDC food decided by rule", f"{fdc_id} {row['description']}: {outcome} ({decision.reason})")
    return decision.duplicate_of
