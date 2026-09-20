"""Reads and shape-validates curation/; references are checked at build time."""

from dataclasses import dataclass, field
from pathlib import Path

import yaml

FDC_NEW = "new"

# Ordered from least to most restrictive; a recipe takes the class of its strictest ingredient.
DIET_CLASSES = ("VEGAN", "VEGETARIAN", "MEAT")


class InvalidCuration(ValueError):
    pass


@dataclass(frozen=True)
class SourceInclusion:
    excluded_groups: frozenset[str]
    include: frozenset[str]
    exclude: frozenset[str]
    # FDC only: SR Legacy food groups that BLS and Foundation Foods lack, taken in whole.
    sr_legacy_groups: frozenset[str] = frozenset()


@dataclass(frozen=True)
class Families:
    # A food listed here is a variant of the given base, wherever its code puts it.
    attach: dict[str, str]
    # A family without an unprepared entry names its base here.
    bases: frozenset[str]


@dataclass(frozen=True)
class CuratedPortion:
    unit: str
    grams: float
    origin: str
    fdc_id: int | None = None


@dataclass(frozen=True)
class Properties:
    """Corrections to a food's physical properties; None leaves the rule's value."""
    density_g_per_ml: float | None = None
    negligible: bool | None = None


@dataclass(frozen=True)
class DietClasses:
    """
    Which foods are animal.

    The BLS group letter decides; `review_patterns` only nominate a food whose name disagrees with
    its group, and `overrides` are the only thing that settles one. See catalogue/diets.py for why
    a pattern is never allowed to decide on its own.
    """
    # BLS group letter -> class. A group left out has no default, so each of its foods needs an override.
    group_defaults: dict[str, str]
    # Class -> regular expression over the source description.
    review_patterns: dict[str, str]
    overrides: dict[str, str]


@dataclass(frozen=True)
class FdcVocabulary:
    """How the FDC duplicate rule reads FDC's American English in the BLS's British English."""
    synonyms: dict[str, str]
    # An FDC word that implies another the BLS spells out: an unqualified "egg" is a chicken egg.
    implied: dict[str, str]
    # Phrases that say nothing about a food ("year round average").
    ignored_phrases: tuple[str, ...]


@dataclass(frozen=True)
class Curation:
    label: str
    bls: SourceInclusion
    fdc: SourceInclusion
    families: Families
    # Corrections to the FDC duplicate rule: FDC id -> BLS code, or "new".
    fdc_duplicates: dict[int, str]
    fdc_vocabulary: FdcVocabulary
    # Names that replace a food's derived names; the first per language is shown.
    names: dict[str, dict[str, list[str]]]
    portions: dict[str, list[CuratedPortion]]
    diet_classes: DietClasses
    properties: dict[str, Properties] = field(default_factory=dict)
    # Everyday names added to a food's other names ("Kartoffel" for "Kartoffel geschält"), never shown first.
    synonyms: dict[str, dict[str, list[str]]] = field(default_factory=dict)


def load(curation: Path) -> Curation:
    inclusion = _read(curation / "inclusion.yaml")
    return Curation(
        label=str(_read(curation / "dataset.yaml")["label"]),
        bls=_inclusion(inclusion["bls"], "excludedGroups"),
        fdc=_inclusion(inclusion["fdc"], "excludedCategories"),
        families=_families(_read(curation / "families.yaml")),
        fdc_duplicates=_fdc_duplicates(_read(curation / "fdc-duplicates.yaml")),
        fdc_vocabulary=_fdc_vocabulary(_read(curation / "fdc-vocabulary.yaml")),
        names=_names(sorted((curation / "names").glob("*.yaml"))),
        diet_classes=_diet_classes(_read(curation / "diet-classes.yaml")),
        portions=_portions(_read(curation / "portions.yaml")),
        properties=_properties(_read(curation / "properties.yaml")),
        synonyms=_names(sorted((curation / "synonyms").glob("*.yaml"))),
    )


def _inclusion(document: dict, excluded_key: str) -> SourceInclusion:
    return SourceInclusion(
        excluded_groups=frozenset(document.get(excluded_key) or ()),
        include=frozenset(str(code) for code in document.get("include") or ()),
        exclude=frozenset(str(code) for code in document.get("exclude") or ()),
        sr_legacy_groups=frozenset(document.get("srLegacyCategories") or ()),
    )


def _families(document: dict) -> Families:
    return Families(
        attach={str(variant): str(base) for variant, base in (document.get("attach") or {}).items()},
        bases=frozenset(str(code) for code in document.get("bases") or ()),
    )


def _fdc_duplicates(document: dict) -> dict[int, str]:
    decisions = {}
    for fdc_id, decision in (document or {}).items():
        if not isinstance(fdc_id, int) or not isinstance(decision, str):
            raise InvalidCuration(f"fdc-duplicates.yaml: '{fdc_id}: {decision}' is not an FDC id with a BLS code or '{FDC_NEW}'")
        decisions[fdc_id] = decision
    return decisions


def _fdc_vocabulary(document: dict) -> FdcVocabulary:
    return FdcVocabulary(
        synonyms={str(word): str(synonym) for word, synonym in (document.get("synonyms") or {}).items()},
        implied={str(word): str(implied) for word, implied in (document.get("implied") or {}).items()},
        ignored_phrases=tuple(str(phrase) for phrase in document.get("ignoredPhrases") or ()),
    )


def _names(paths: list[Path]) -> dict[str, dict[str, list[str]]]:
    names: dict[str, dict[str, list[str]]] = {}
    for path in paths:
        for key, languages in (_read(path) or {}).items():
            if key in names:
                raise InvalidCuration(f"names for {key} are given twice (second time in {path.name})")
            if not isinstance(languages, dict) or not all(isinstance(words, list) and words for words in languages.values()):
                raise InvalidCuration(f"{path.name}: names for {key} must map languages to non-empty lists")
            names[key] = {language: [str(word) for word in words] for language, words in languages.items()}
    return names


def _portions(document: dict) -> dict[str, list[CuratedPortion]]:
    portions = {}
    for key, units in (document or {}).items():
        portions[key] = []
        for unit, spec in units.items():
            fdc_id = spec.get("fdc")
            portion = CuratedPortion(unit=unit, grams=float(spec["grams"]),
                                     origin="SOURCE" if fdc_id is not None else "ESTIMATED", fdc_id=fdc_id)
            if portion.grams <= 0:
                raise InvalidCuration(f"portion {unit} of {key} must weigh more than nothing")
            portions[key].append(portion)
    return portions


def _diet_classes(document: dict) -> DietClasses:
    return DietClasses(
        group_defaults={str(group): _diet_class(value, f"groupDefaults {group}")
                        for group, value in (document.get("groupDefaults") or {}).items()},
        review_patterns={_diet_class(name, f"reviewPatterns key '{name}'"): str(pattern)
                         for name, pattern in (document.get("reviewPatterns") or {}).items()},
        overrides={str(key): _diet_class(value, f"override {key}")
                   for key, value in (document.get("overrides") or {}).items()},
    )


def _diet_class(value: object, where: str) -> str:
    name = str(value).upper()
    if name not in DIET_CLASSES:
        raise InvalidCuration(f"diet-classes.yaml: {where} is '{value}', not one of {', '.join(DIET_CLASSES)}")
    return name


def _properties(document: dict) -> dict[str, Properties]:
    return {key: Properties(density_g_per_ml=spec.get("density"), negligible=spec.get("negligible"))
            for key, spec in (document or {}).items()}


def _read(path: Path) -> dict:
    with path.open(encoding="utf-8") as file:
        return yaml.safe_load(file) or {}
