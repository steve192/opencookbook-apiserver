"""What the catalogue is made of, and the report every build step writes its decisions to."""

from collections import defaultdict
from dataclasses import dataclass, field
from typing import Iterable

from cookpal_nutrition.curation import InvalidCuration
from cookpal_nutrition.nutrients import Nutrients

LANGUAGES = ("de", "en")


@dataclass(frozen=True)
class Name:
    language: str
    text: str
    display: bool


@dataclass(frozen=True)
class Portion:
    unit: str
    grams: float
    origin: str


@dataclass
class Food:
    key: str
    source_type: str
    source_code: str
    source_name: str
    # The source's description per language it is published in.
    source_names: dict[str, str]
    states: tuple[str, ...]
    nutrients: Nutrients
    variant_of: str | None = None
    names: list[Name] = field(default_factory=list)
    portions: list[Portion] = field(default_factory=list)
    density_g_per_ml: float | None = None
    negligible: bool = False
    # VEGAN, VEGETARIAN or MEAT; see catalogue/diets.py.
    diet_class: str | None = None


@dataclass(frozen=True)
class Sources:
    bls_foods: list[dict[str, str]]
    fdc_foods: list[dict[str, str]]
    fdc_portions: list[dict[str, str]]


@dataclass
class Report:
    """Decisions taken by rule, and curation that no longer applies. Nothing in here stops a build."""
    lines: dict[str, list[str]] = field(default_factory=lambda: defaultdict(list))

    def add(self, topic: str, line: str) -> None:
        self.lines[topic].append(line)

    def stale(self, file: str, references: Iterable[object]) -> None:
        for reference in sorted(str(reference) for reference in references):
            self.add(f"stale curation in {file} (ignored)", reference)


class UndecidedFoods(InvalidCuration):
    """Stops a build that would ship a guess; carries the report listing what to decide."""

    def __init__(self, message: str, report: Report) -> None:
        super().__init__(message)
        self.report = report


def bls_key(code: str) -> str:
    return f"bls-{code}"


def fdc_key(fdc_id: object) -> str:
    return f"fdc-{fdc_id}"
