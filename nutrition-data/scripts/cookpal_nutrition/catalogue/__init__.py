"""Assembles the catalogue from source extracts, reference data and curation.

Every decision is taken by rule; curation corrects single foods. Fails only where shipping would be wrong.
"""

from cookpal_nutrition.catalogue import amounts, bls_foods, diets, fdc_foods, naming
from cookpal_nutrition.catalogue.model import (LANGUAGES, Food, Name, Portion, Report, Sources, UndecidedFoods,
                                               bls_key, fdc_key)
from cookpal_nutrition.catalogue.words import StateWords
from cookpal_nutrition.curation import Curation
from cookpal_nutrition.reference import Reference

__all__ = ["LANGUAGES", "Food", "Name", "Portion", "Report", "Sources", "UndecidedFoods", "bls_key", "fdc_key", "build"]


def build(reference: Reference, curation: Curation, sources: Sources) -> tuple[list[Food], Report]:
    report = Report()
    words = StateWords(reference)
    foods = bls_foods.build(reference, curation, sources.bls_foods, words, report)
    foods.update(fdc_foods.build(curation, sources.fdc_foods, sources.bls_foods, foods, words, report))
    naming.assign(curation, foods, words, report)
    amounts.assign(reference, curation, sources, foods, report)
    diets.assign(curation, foods, report)
    return sorted(foods.values(), key=lambda food: food.key), report
