"""Where a catalogue food is sold, for sorting a shopping list the way a shop is walked.

The first two characters of a BLS code decide. The group letter alone is too coarse: E holds eggs
and pasta, C flour and rice, Q oils and butter, S sugar and ice cream. A processing state moves a
food whatever its group, since canned peas are shelved with the cans and not with the vegetables.
A few words in a source name say how the product itself is sold ("tiefgefroren", "-saft"); unlike the
words a diet class distrusts, they describe the product and not an ingredient, so they may decide.
FDC foods have no code, so each is curated. A food nobody placed stops the build, as a diet class
does: a new release must not quietly put Rindsbouillon next to the toilet paper.
"""

import re

from cookpal_catalogue.catalogue.model import Food, Report, UndecidedFoods
from cookpal_catalogue.curation import Aisles, Curation

UNDECIDED = "food without an aisle (no subgroup default, no override)"
SUBGROUP_LENGTH = 2


def assign(curation: Curation, foods: dict[str, Food], report: Report) -> None:
    aisles = curation.aisles
    report.stale("aisles.yaml", aisles.overrides.keys() - foods.keys())
    patterns = {aisle: re.compile(pattern, re.IGNORECASE) for aisle, pattern in aisles.source_name_patterns.items()}

    undecided = 0
    for key, food in sorted(foods.items()):
        if food.variant_of is None:
            food.aisle = _decide(aisles, patterns, key, food, fallback=_subgroup_default(aisles, food))
            if food.aisle is None:
                report.add(UNDECIDED, f"{key} {food.source_name}")
                undecided += 1
    for key, food in foods.items():
        if food.variant_of is not None:
            base = foods.get(food.variant_of)
            food.aisle = _decide(aisles, patterns, key, food, fallback=base.aisle if base else None)

    if undecided:
        raise UndecidedFoods(
            f"{undecided} food(s) have no aisle. Place each in curation/aisles.yaml under 'overrides'; "
            f"they are listed in the build report under '{UNDECIDED}'.", report)


def _decide(aisles: Aisles, patterns: dict[str, re.Pattern], key: str, food: Food,
            fallback: str | None) -> str | None:
    by_state = next((aisles.state_defaults[state] for state in food.states if state in aisles.state_defaults), None)
    by_name = next((aisle for aisle, pattern in patterns.items() if pattern.search(food.source_name)), None)
    return aisles.overrides.get(key) or by_state or by_name or fallback


def _subgroup_default(aisles: Aisles, food: Food) -> str | None:
    if food.source_type != "BLS" or len(food.source_code) < SUBGROUP_LENGTH:
        return None
    return aisles.subgroup_defaults.get(food.source_code[:SUBGROUP_LENGTH])
