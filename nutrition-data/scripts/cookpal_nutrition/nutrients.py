"""Nutrient values per 100 g, harmonised to EU labelling (Regulation 1169/2011).

FDC differs from BLS: carbohydrates include fibre, protein uses food-specific factors, sodium instead of salt.
Energy is always recomputed with the EU factors, as BLS does. Parts are capped at their whole
(saturated fat <= fat, sugar <= carbohydrates), as trace measurements can exceed it.
"""

from dataclasses import dataclass, fields

from cookpal_nutrition.sources import bls
from cookpal_nutrition.tables import number

NITROGEN_TO_PROTEIN = 6.25
SALT_PER_SODIUM = 2.5


@dataclass(frozen=True)
class EnergyFactors:
    """Energy per gram of each constituent, per Annex XIV of Regulation 1169/2011."""
    protein: float
    carbohydrate: float
    fat: float
    alcohol: float
    organic_acid: float
    polyol: float
    fibre: float


KCAL = EnergyFactors(protein=4, carbohydrate=4, fat=9, alcohol=7, organic_acid=3, polyol=2.4, fibre=2)
KJ = EnergyFactors(protein=17, carbohydrate=17, fat=37, alcohol=29, organic_acid=13, polyol=10, fibre=8)


@dataclass(frozen=True)
class Nutrients:
    energy_kcal: float
    energy_kj: float
    fat: float
    saturated_fat: float | None
    carbohydrates: float
    sugar: float | None
    fibre: float | None
    protein: float
    salt: float | None

    def as_dict(self) -> dict[str, float | None]:
        return {field.name: getattr(self, field.name) for field in fields(self)}


@dataclass(frozen=True)
class Composition:
    """What energy is computed from. `carbohydrates` is available carbohydrate including polyols."""
    protein: float
    carbohydrates: float
    fat: float
    alcohol: float = 0.0
    organic_acids: float = 0.0
    polyols: float = 0.0
    oligosaccharides: float = 0.0
    fibre: float = 0.0

    def energy(self, factors: EnergyFactors) -> float:
        # Oligosaccharides count like fibre, and polyols are part of the carbohydrate figure.
        return (factors.protein * self.protein
                + factors.carbohydrate * (self.carbohydrates - self.polyols)
                + factors.fat * self.fat
                + factors.alcohol * self.alcohol
                + factors.organic_acid * self.organic_acids
                + factors.polyol * self.polyols
                + factors.fibre * (self.oligosaccharides + self.fibre))


class IncompleteNutrients(ValueError):
    """A food lacks protein, fat or carbohydrates, so its energy cannot be computed."""


def from_bls(row: dict[str, str]) -> Nutrients:
    values = {component: bls.value(row[component]) for component in bls.COMPONENTS}
    composition = _composition(
        protein=values["PROT625"], carbohydrates=values["CHO"], fat=values["FAT"],
        alcohol=values["ALC"], organic_acids=values["OA"], polyols=values["POLYL"],
        oligosaccharides=values["OLSAC"], fibre=values["FIBT"], food=row["code"])
    return _nutrients(composition, saturated_fat=values["FASAT"], sugar=values["SUGAR"], fibre=values["FIBT"],
                      salt=values["NACL"])


def from_fdc(row: dict[str, str]) -> Nutrients:
    fibre = number(row["fibre"])
    composition = _composition(
        protein=_fdc_protein(row), carbohydrates=_fdc_available_carbohydrates(row, fibre),
        fat=number(row["fat"]), alcohol=number(row["alcohol"]), fibre=fibre, food=row["fdc_id"])
    sodium = number(row["sodium_mg"])
    return _nutrients(composition, saturated_fat=number(row["saturated_fat"]),
                      sugar=_first_known(number(row["sugars_total_nlea"]), number(row["sugars_total"])), fibre=fibre,
                      salt=None if sodium is None else sodium * SALT_PER_SODIUM / 1000)


def _fdc_protein(row: dict[str, str]) -> float | None:
    nitrogen = number(row["nitrogen"])
    if nitrogen is not None:
        return nitrogen * NITROGEN_TO_PROTEIN
    protein = number(row["protein"])
    factor = number(row["protein_conversion_factor"])
    if protein is not None and factor:
        return protein / factor * NITROGEN_TO_PROTEIN
    return protein


def _fdc_available_carbohydrates(row: dict[str, str], fibre: float | None) -> float | None:
    by_summation = number(row["carbohydrate_by_summation"])
    if by_summation is not None:
        return by_summation
    by_difference = number(row["carbohydrate_by_difference"])
    if by_difference is None:
        return None
    return max(0.0, by_difference - (fibre or 0.0))


def _composition(protein, carbohydrates, fat, food, **optional) -> Composition:
    if protein is None or carbohydrates is None or fat is None:
        raise IncompleteNutrients(f"{food} lacks protein, carbohydrates or fat")
    return Composition(protein=protein, carbohydrates=carbohydrates, fat=fat,
                       **{name: value or 0.0 for name, value in optional.items()})


def _nutrients(composition: Composition, saturated_fat, sugar, fibre, salt) -> Nutrients:
    return Nutrients(
        energy_kcal=round(composition.energy(KCAL), 1),
        energy_kj=round(composition.energy(KJ), 1),
        fat=composition.fat,
        saturated_fat=_part_of(saturated_fat, composition.fat),
        carbohydrates=composition.carbohydrates,
        sugar=_part_of(sugar, composition.carbohydrates),
        fibre=fibre,
        protein=round(composition.protein, 3),
        salt=None if salt is None else round(salt, 4),
    )


def _part_of(part: float | None, whole: float) -> float | None:
    return None if part is None else min(part, whole)


def _first_known(*values: float | None) -> float | None:
    return next((value for value in values if value is not None), None)
