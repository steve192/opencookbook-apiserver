"""BLS foods: inclusion, states and variants, by rule from the BLS structure.

inclusion.yaml and families.yaml correct single foods; corrections to codes no longer present are reported.
"""

import re
from collections import defaultdict

from cookpal_nutrition import nutrients as nutrient_values
from cookpal_nutrition.catalogue.model import Food, Report, bls_key
from cookpal_nutrition.catalogue.words import StateWords
from cookpal_nutrition.curation import Curation, InvalidCuration
from cookpal_nutrition.nutrients import IncompleteNutrients
from cookpal_nutrition.reference import Reference
from cookpal_nutrition.sources import bls

SOURCE_LANGUAGES = {"de": "name_de", "en": "name_en"}
# Only purely numeric codes follow the old BLS structure in which position 6 is the preparation;
# in codes with letters inside, position 6 means something else (see SOURCES.md).
STRUCTURED_CODE = re.compile(r"[A-Z]\d{6}")
FAMILY_PREFIX = 5

ENERGY_TOLERANCE_SHARE = 0.03
ENERGY_TOLERANCE_KCAL = 2.0
MAX_ENERGY_DEVIATION_SHARE = 0.01


def build(reference: Reference, curation: Curation, rows: list[dict[str, str]], words: StateWords,
          report: Report) -> dict[str, Food]:
    by_code = {row["code"]: row for row in rows}
    report.stale("inclusion.yaml (bls)", (curation.bls.include | curation.bls.exclude) - by_code.keys())
    included = [row for code, row in by_code.items() if _included(code, curation)]
    _require_energy_formula_matches(included)

    foods: dict[str, Food] = {}
    for row in included:
        try:
            food_nutrients = nutrient_values.from_bls(row)
        except IncompleteNutrients as incomplete:
            report.add("excluded, incomplete nutrients", str(incomplete))
            continue
        foods[bls_key(row["code"])] = Food(
            key=bls_key(row["code"]), source_type="BLS", source_code=row["code"], source_name=row["name_de"],
            source_names={language: row[column] for language, column in SOURCE_LANGUAGES.items()},
            states=tuple(sorted(_states(reference, row, words, report))), nutrients=food_nutrients)
    _link_variants(reference, curation, foods, words, report)
    return foods


def _included(code: str, curation: Curation) -> bool:
    if code in curation.bls.exclude:
        return False
    return code in curation.bls.include or code[0] not in curation.bls.excluded_groups


def _require_energy_formula_matches(rows: list[dict[str, str]]) -> None:
    """Checks the energy recomputation against BLS; fails when BLS changes its formula."""
    checked, deviating = 0, []
    for row in rows:
        published = bls.value(row["ENERCC"])
        try:
            computed = nutrient_values.from_bls(row).energy_kcal
        except IncompleteNutrients:
            continue
        checked += 1
        if abs(computed - published) > max(ENERGY_TOLERANCE_KCAL, published * ENERGY_TOLERANCE_SHARE):
            deviating.append(f"{row['code']} {row['name_de']}: BLS {published} kcal, recomputed {computed} kcal")
    if checked and len(deviating) / checked > MAX_ENERGY_DEVIATION_SHARE:
        raise InvalidCuration("energy recomputation disagrees with BLS for too many foods:\n  " + "\n  ".join(deviating[:20]))


def _states(reference: Reference, row: dict[str, str], words: StateWords, report: Report) -> set[str]:
    processing = {state for language, column in SOURCE_LANGUAGES.items()
                  for state in words.processings_named(row[column], language)}
    named = {state for language, column in SOURCE_LANGUAGES.items()
             for state in words.preparations_named(row[column], language)}
    coded = _coded_preparation(reference, row["code"])
    if coded is None:
        # Foods sold prepared ("gekocht, Konserve") carry their preparation in the name only.
        return processing | named
    if coded not in named:
        report.add("preparation in code but not in name", f"{row['code']} {row['name_de']}: code says {coded}")
    return processing | {coded}


def _coded_preparation(reference: Reference, code: str) -> str | None:
    return reference.bls_preparation_digits.get(code[5]) if STRUCTURED_CODE.fullmatch(code) else None


def _link_variants(reference: Reference, curation: Curation, foods: dict[str, Food], words: StateWords,
                   report: Report) -> None:
    """Makes each prepared food a variant of its unprepared food, the first found of:
    families.yaml; the family's only unprepared food; the unprepared food named like it without preparation
    words; an all-prepared family's member whose code says unprepared. Unprepared foods are never variants.
    """
    by_code = {food.source_code: food for food in foods.values()}
    attach = _valid_attachments(curation, by_code, report)
    declared_bases = curation.families.bases & by_code.keys()
    report.stale("families.yaml (bases)", curation.families.bases - by_code.keys())
    for variant, base in attach.items():
        by_code[variant].variant_of = bls_key(base)

    free = [food for code, food in by_code.items() if code not in attach]
    unprepared_by_name: dict[str, list[Food]] = defaultdict(list)
    for food in free:
        if not words.is_prepared(food):
            unprepared_by_name[_unprepared_name(food, words)].append(food)

    families: dict[str, list[Food]] = defaultdict(list)
    for food in free:
        families[food.source_code[:FAMILY_PREFIX]].append(food)

    for members in families.values():
        family_base = _family_base(members, declared_bases, words)
        coded_base = _coded_base(reference, members) if family_base is None else None
        for member in members:
            if member is family_base or member.source_code in declared_bases or not words.is_prepared(member):
                continue
            base = family_base or _named_base(member, unprepared_by_name, words, report)
            if base is None and member is not coded_base:
                base = coded_base
            if base is None:
                if member is not coded_base:
                    report.add("prepared food without an unprepared base (kept as a food of its own)",
                               f"{member.source_code} {member.source_name}")
                continue
            member.variant_of = base.key


def _valid_attachments(curation: Curation, by_code: dict[str, Food], report: Report) -> dict[str, str]:
    valid = {variant: base for variant, base in curation.families.attach.items()
             if variant in by_code and base in by_code}
    report.stale("families.yaml (attach)", (f"{variant}: {base}" for variant, base in curation.families.attach.items()
                                            if variant not in valid))
    chained = [variant for variant, base in valid.items() if base in valid]
    if chained:
        raise InvalidCuration(f"families.yaml attaches foods to foods that are themselves attached: {sorted(chained)}")
    return valid


def _family_base(members: list[Food], declared_bases: set[str], words: StateWords) -> Food | None:
    """The base families.yaml names, else the family's only unprepared food."""
    declared = [member for member in members if member.source_code in declared_bases]
    if declared:
        return declared[0] if len(declared) == 1 else None
    unprepared = [member for member in members if not words.is_prepared(member)]
    return unprepared[0] if len(unprepared) == 1 else None


def _named_base(member: Food, unprepared_by_name: dict[str, list[Food]], words: StateWords, report: Report) -> Food | None:
    named = unprepared_by_name.get(_unprepared_name(member, words), [])
    if len(named) != 1:
        return None
    report.add("variant linked by name", f"{member.source_code} {member.source_name} -> "
                                         f"{named[0].source_code} {named[0].source_name}")
    return named[0]


def _coded_base(reference: Reference, members: list[Food]) -> Food | None:
    coded = [member for member in members
             if STRUCTURED_CODE.fullmatch(member.source_code) and _coded_preparation(reference, member.source_code) is None]
    return coded[0] if len(coded) == 1 else None


def _unprepared_name(food: Food, words: StateWords) -> str:
    derived = words.names_from_description(food, "de")
    return derived[0].casefold() if derived else ""
