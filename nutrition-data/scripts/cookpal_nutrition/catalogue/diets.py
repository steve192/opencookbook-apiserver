"""Which catalogue foods are animal: VEGAN, VEGETARIAN or MEAT.

The BLS group letter decides. It carries the food's kind - U, V and W are meat, offal and sausage,
T is fish, M dairy, E eggs - so it settles the overwhelming majority without anyone reading a name.

Names only *nominate*. Measured over the shipped catalogue, the obvious patterns are wrong often
enough that none may decide: "Wildreis" matches wild, "Fondant" matches fond, "Erdnussbutter" and
"Butterbohne" match butter, "Honigmelone" matches honig, "alkoholfrei" matches ei, and
"Vegetarische Bratwurst" matches wurst - every one of them a plant food the patterns call animal.
So a name that disagrees with its group only asks a person to look, and an unanswered question
fails the build. A new BLS release cannot quietly introduce an unclassified Rindsbouillon.

Fish counts as MEAT: the diet scale has three levels, VEGAN, VEGETARIAN and MEAT.
"""

import re

from cookpal_nutrition.catalogue.model import Food, Report, UndecidedFoods
from cookpal_nutrition.curation import DIET_CLASSES, Curation, DietClasses

UNDECIDED = "food without a diet class (no group default, no override)"
NOMINATED = "diet class nominated for review (name disagrees with group)"
CORRECTED = "diet class overridden against its group"


def assign(curation: Curation, foods: dict[str, Food], report: Report) -> None:
    diets = curation.diet_classes
    report.stale("diet-classes.yaml", diets.overrides.keys() - foods.keys())
    patterns = {name: re.compile(pattern, re.IGNORECASE) for name, pattern in diets.review_patterns.items()}

    open_questions = 0
    for key, food in sorted(foods.items()):
        if food.variant_of is not None:
            continue
        open_questions += _decide(diets, patterns, key, food, report)
    _inherit_to_variants(diets, foods)

    if open_questions:
        raise UndecidedFoods(
            f"{open_questions} food(s) have no reviewed diet class. Decide each in curation/diet-classes.yaml "
            f"under 'overrides'; they are listed in the build report under '{UNDECIDED}' and '{NOMINATED}'.",
            report)


def _decide(diets: DietClasses, patterns: dict[str, re.Pattern], key: str, food: Food, report: Report) -> int:
    """Sets the food's class, or reports why it cannot be settled. Returns the open questions it left."""
    override = diets.overrides.get(key)
    default = _group_default(diets, food)

    if override is not None:
        food.diet_class = override
        if default is not None and override != default:
            report.add(CORRECTED, f"{key} {food.source_name}: group says {default}, curated as {override}")
        return 0

    if default is None:
        report.add(UNDECIDED, f"{key} {food.source_name}")
        return 1

    suggested = _suggested_by_name(patterns, food, default)
    if suggested is not None:
        report.add(NOMINATED, f"{key} {food.source_name}: group says {default}, name suggests {suggested}")
        return 1

    food.diet_class = default
    return 0


def _group_default(diets: DietClasses, food: Food) -> str | None:
    """FDC foods have no group letter, so each is curated by hand; there are barely a hundred."""
    if food.source_type != "BLS" or not food.source_code:
        return None
    return diets.group_defaults.get(food.source_code[0])


def _suggested_by_name(patterns: dict[str, re.Pattern], food: Food, default: str) -> str | None:
    """
    The strictest class a pattern reads into the name, if that is stricter than the group's.

    Only upwards: a plant word in a meat group says nothing, since a sausage stays a sausage
    however much soy its description mentions.
    """
    for name in reversed(DIET_CLASSES):
        if DIET_CLASSES.index(name) <= DIET_CLASSES.index(default):
            return None
        if name in patterns and patterns[name].search(food.source_name):
            return name
    return None


def _inherit_to_variants(diets: DietClasses, foods: dict[str, Food]) -> None:
    """A preparation cannot differ in diet from what it is a preparation of, unless curated to."""
    for key, food in foods.items():
        if food.variant_of is None:
            continue
        base = foods.get(food.variant_of)
        food.diet_class = diets.overrides.get(key) or (base.diet_class if base else None)
