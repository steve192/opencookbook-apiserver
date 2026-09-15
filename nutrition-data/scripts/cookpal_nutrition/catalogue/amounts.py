"""Portion weights, densities and negligibility of catalogue foods.

Rules: weights and densities from FDC portions; negligible when nearly without energy or a spice.
curation/portions.yaml and properties.yaml override them.
"""

from cookpal_nutrition.catalogue import fdc_amounts
from cookpal_nutrition.catalogue.model import Food, Portion, Report, Sources
from cookpal_nutrition.curation import Curation, CuratedPortion, InvalidCuration
from cookpal_nutrition.reference import Reference

NEGLIGIBLE_KCAL_PER_100_G = 5.0
# Spices are rich per 100 g but used by the pinch: a recipe's "Pfeffer" without an amount changes nothing.
BLS_SPICE_GROUP = "R2"
FDC_SPICE_CATEGORY = "Spices and Herbs"


def assign(reference: Reference, curation: Curation, sources: Sources, foods: dict[str, Food], report: Report) -> None:
    derived = fdc_amounts.derive(reference, curation, sources.fdc_foods, sources.fdc_portions, sources.bls_foods, foods, report)
    fdc_spices = {row["fdc_id"] for row in sources.fdc_foods if row.get("category") == FDC_SPICE_CATEGORY}
    for key, food in foods.items():
        food.portions = list(derived.portions.get(key, ()))
        food.density_g_per_ml = derived.densities.get(key)
        food.negligible = food.nutrients.energy_kcal <= NEGLIGIBLE_KCAL_PER_100_G or _is_spice(food, fdc_spices)
    _apply_curated_portions(reference, curation, sources.fdc_portions, foods, report)
    _apply_curated_properties(curation, foods, report)


def _is_spice(food: Food, fdc_spices: set[str]) -> bool:
    if food.source_type == "BLS":
        return food.source_code.startswith(BLS_SPICE_GROUP)
    return food.source_code in fdc_spices


def _apply_curated_portions(reference: Reference, curation: Curation, fdc_portions: list[dict[str, str]],
                            foods: dict[str, Food], report: Report) -> None:
    cited = _fdc_gram_weights(fdc_portions)
    report.stale("portions.yaml", curation.portions.keys() - foods.keys())
    for key, portions in curation.portions.items():
        if key not in foods:
            continue
        for portion in portions:
            _require_valid(reference, key, portion, cited)
        curated_units = {portion.unit for portion in portions}
        foods[key].portions = sorted([portion for portion in foods[key].portions if portion.unit not in curated_units]
                                     + [Portion(unit=portion.unit, grams=portion.grams, origin=portion.origin) for portion in portions],
                                     key=lambda portion: portion.unit)


def _apply_curated_properties(curation: Curation, foods: dict[str, Food], report: Report) -> None:
    report.stale("properties.yaml", curation.properties.keys() - foods.keys())
    for key, properties in curation.properties.items():
        if key not in foods:
            continue
        density = properties.density_g_per_ml
        if density is not None:
            if not fdc_amounts.DENSITY_RANGE[0] <= density <= fdc_amounts.DENSITY_RANGE[1]:
                raise InvalidCuration(f"properties.yaml: density {density} g/ml of {key} is outside {fdc_amounts.DENSITY_RANGE}")
            foods[key].density_g_per_ml = density
        if properties.negligible is not None:
            foods[key].negligible = properties.negligible


def _fdc_gram_weights(fdc_portions: list[dict[str, str]]) -> dict[int, set[float]]:
    """Grams of one unit, per FDC food, as a curated portion citing that food must state them."""
    weights: dict[int, set[float]] = {}
    for portion in fdc_portions:
        amount = float(portion["amount"] or 1) or 1.0
        weights.setdefault(int(portion["fdc_id"]), set()).add(round(float(portion["gram_weight"]) / amount, 1))
    return weights


def _require_valid(reference: Reference, key: str, portion: CuratedPortion, cited: dict[int, set[float]]) -> None:
    unit = reference.units.get(portion.unit)
    if unit is None or unit.kind != "COUNT" or unit.size_of is not None:
        raise InvalidCuration(f"portions.yaml: {key} gives a weight for '{portion.unit}', which is not a plain count unit")
    if portion.grams > fdc_amounts.MAX_PORTION_GRAMS:
        raise InvalidCuration(f"portions.yaml: {key} {portion.unit} weighs {portion.grams} g, more than plausible")
    if portion.fdc_id is not None and round(portion.grams, 1) not in cited.get(portion.fdc_id, set()):
        raise InvalidCuration(f"portions.yaml: {key} cites FDC {portion.fdc_id} for {portion.grams} g, but that food has no such portion")
