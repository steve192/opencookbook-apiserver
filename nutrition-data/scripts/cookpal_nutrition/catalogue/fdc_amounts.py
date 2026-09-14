"""Portion weights and densities from FDC household measures.

Each FDC food maps to its catalogue food (itself, or the BLS food it duplicates, with a looser energy tolerance).
Count measures ("clove", "medium") give piece weights, size words scaled ("1 large" 182 g -> piece 140 g);
volume measures give density. The median of several values counts; weight and serving measures are skipped.
"""

import re
import statistics
from collections import defaultdict
from dataclasses import dataclass

from cookpal_nutrition import fdc_duplicates
from cookpal_nutrition import nutrients as nutrient_values
from cookpal_nutrition.catalogue.fdc_foods import bls_candidates, vocabulary_of
from cookpal_nutrition.catalogue.model import Food, Portion, Report, bls_key, fdc_key
from cookpal_nutrition.curation import FDC_NEW, Curation
from cookpal_nutrition.nutrients import IncompleteNutrients
from cookpal_nutrition.reference import Reference

MEASURE_LANGUAGE = "en"
DENSITY_RANGE = (0.2, 2.0)
MAX_PORTION_GRAMS = 5000
_LEADING_WORDS = re.compile(r"[a-z][a-z .]*[a-z.]")


@dataclass(frozen=True)
class Amounts:
    portions: dict[str, list[Portion]]
    densities: dict[str, float]


def derive(reference: Reference, curation: Curation, fdc_rows: list[dict[str, str]], fdc_portions: list[dict[str, str]],
           bls_rows: list[dict[str, str]], foods: dict[str, Food], report: Report) -> Amounts:
    owners = _catalogue_food_of_each_fdc_food(curation, fdc_rows, bls_rows, foods)
    measures = _MeasureReader(reference)
    grams: dict[str, dict[str, list[float]]] = defaultdict(lambda: defaultdict(list))
    densities: dict[str, list[float]] = defaultdict(list)

    for row in fdc_portions:
        key = owners.get(int(row["fdc_id"]))
        amount, weight = float(row["amount"] or 1) or 1.0, float(row["gram_weight"] or 0)
        measure = measures.read(row["measure_unit"], row["modifier"]) if key and weight > 0 else None
        if measure is None:
            continue
        unit, factor = measure
        if unit.kind == "VOLUME":
            densities[key].append(weight / (amount * unit.millilitres))
        elif unit.kind == "COUNT":
            grams[key][unit.size_of or unit.key].append(weight / amount / (factor or 1.0))

    portions = {key: [Portion(unit=unit, grams=round(statistics.median(values), 1), origin="SOURCE")
                      for unit, values in sorted(by_unit.items()) if statistics.median(values) <= MAX_PORTION_GRAMS]
                for key, by_unit in grams.items()}
    medians = {key: round(statistics.median(values), 3) for key, values in densities.items()}
    plausible = {key: density for key, density in medians.items() if DENSITY_RANGE[0] <= density <= DENSITY_RANGE[1]}
    for key in sorted(medians.keys() - plausible.keys()):
        report.add("implausible density from FDC portions (ignored)", f"{key}: {medians[key]} g/ml")
    report.add("summary", f"FDC portions: weights for {len(portions)} foods, densities for {len(plausible)} foods")
    return Amounts(portions=portions, densities=plausible)


class _MeasureReader:
    """Reads the unit an FDC portion is measured in, with the English unit words of the lexicon."""

    def __init__(self, reference: Reference):
        self._units = reference.units
        words = reference.lexicons[MEASURE_LANGUAGE].units
        self._by_word = {word.lower(): key for key, unit_words in words.items() for word in unit_words}
        self._longest = max(len(word.split()) for word in self._by_word)

    def read(self, measure_unit: str, modifier: str):
        """(unit, size factor) for a count or volume measure, or None."""
        text = f"{measure_unit or ''} {modifier or ''}".lower().strip()
        match = _LEADING_WORDS.match(text)
        if not match:
            return None
        tokens = match.group(0).replace(",", " ").split()
        for length in range(min(self._longest, len(tokens)), 0, -1):
            key = self._by_word.get(" ".join(tokens[:length]))
            if key is None:
                continue
            unit = self._units[key]
            if unit.kind in ("VOLUME", "COUNT"):
                return unit, unit.factor
            return None
        return None


def _catalogue_food_of_each_fdc_food(curation: Curation, fdc_rows: list[dict[str, str]], bls_rows: list[dict[str, str]],
                                     foods: dict[str, Food]) -> dict[int, str]:
    vocabulary = vocabulary_of(curation)
    candidates = bls_candidates(vocabulary, bls_rows, foods)
    owners = {}
    for row in fdc_rows:
        fdc_id = int(row["fdc_id"])
        if fdc_key(fdc_id) in foods:
            owners[fdc_id] = fdc_key(fdc_id)
            continue
        corrected = curation.fdc_duplicates.get(fdc_id)
        if corrected == FDC_NEW:
            continue
        if corrected is not None and bls_key(corrected) in foods:
            owners[fdc_id] = bls_key(corrected)
            continue
        try:
            energy = nutrient_values.from_fdc(row).energy_kcal
        except IncompleteNutrients:
            energy = None
        decision = fdc_duplicates.decide(vocabulary.describe(row["description"], energy), candidates, fdc_duplicates.SAME_KIND)
        if decision.duplicate_of:
            owners[fdc_id] = decision.duplicate_of
    return owners
