"""Writes the shipped dataset as deterministic JSON plus a manifest; the apiserver re-imports on a new checksum."""

import hashlib
import json
from dataclasses import asdict
from pathlib import Path

from cookpal_nutrition.catalogue import LANGUAGES, Food
from cookpal_nutrition.extraction import Provenance
from cookpal_nutrition.reference import Reference

CONTENT_FILES = ("catalogue.json", "units.json", "states.json", "lexicons.json")

# Citations as the publisher words them, per release; a new release fails the build until it is added here.
CITATIONS = {
    "BLS": {
        "4.0 (2025)": "Max Rubner-Institut (2025): Bundeslebensmittelschlüssel (BLS), Version 4.0 - Deutsche "
                      "Nährstoffdatenbank. Karlsruhe. DOI: 10.25826/Data20251217-134202-0",
    },
}

# Attribution per source; {releases} and {citation} are filled from the downloads the extracts were made from.
ATTRIBUTIONS = {
    "BLS": {
        "text": "Nährwertdaten: {citation}. Auszug; Werte vereinheitlicht, Bezeichnungen, Portionsgewichte und "
                "Dichten ergänzt.",
        "license": "CC BY 4.0",
        "licenseUrl": "https://creativecommons.org/licenses/by/4.0/",
    },
    "FDC": {
        "text": "Additional data: U.S. Department of Agriculture, Agricultural Research Service. FoodData Central "
                "({releases}), fdc.nal.usda.gov.",
        "license": "CC0 1.0",
        "licenseUrl": "https://creativecommons.org/publicdomain/zero/1.0/",
    },
}


def write(target: Path, label: str, foods: list[Food], reference: Reference, provenance: list[Provenance]) -> str:
    """Writes every file and returns the checksum."""
    target.mkdir(parents=True, exist_ok=True)
    contents = {
        "catalogue.json": {"foods": [_food(food) for food in foods]},
        "units.json": {"units": [_without_empty(asdict(unit)) for unit in reference.units.values()]},
        "states.json": {"states": [_without_empty(asdict(state)) for state in reference.states.values()]},
        "lexicons.json": {"lexicons": [_without_empty(asdict(reference.lexicons[language])) for language in LANGUAGES]},
    }
    digest = hashlib.sha256()
    for name in CONTENT_FILES:
        encoded = _encode(contents[name])
        (target / name).write_bytes(encoded)
        digest.update(encoded)
    checksum = digest.hexdigest()
    manifest = {
        "label": label,
        "checksum": checksum,
        "files": list(CONTENT_FILES),
        "languages": list(LANGUAGES),
        "foods": len(foods),
        "attributions": attributions(provenance),
        "sources": [asdict(entry) for entry in provenance],
    }
    (target / "manifest.json").write_bytes(_encode(manifest))
    return checksum


def attributions(provenance: list[Provenance]) -> list[dict]:
    result = []
    for source, attribution in ATTRIBUTIONS.items():
        releases = [entry.release for entry in provenance if entry.source == source]
        text = attribution["text"].format(releases=", ".join(releases), citation=_citation(source, releases))
        result.append({"source": source, **attribution, "text": text})
    return result


def _citation(source: str, releases: list[str]) -> str:
    citations = CITATIONS.get(source, {})
    missing = [release for release in releases if source in CITATIONS and release not in citations]
    if missing:
        raise ValueError(f"No citation for {source} {', '.join(missing)}: add it to CITATIONS in dataset.py")
    return "; ".join(citations[release] for release in releases if release in citations)


def _food(food: Food) -> dict:
    return _without_empty({
        "key": food.key,
        "variantOf": food.variant_of,
        "source": {"type": food.source_type, "code": food.source_code, "name": food.source_name},
        "states": list(food.states),
        "dietClass": food.diet_class,
        "negligible": food.negligible,
        "densityGPerMl": food.density_g_per_ml,
        "nutrients": _camel(food.nutrients.as_dict()),
        "names": [{"language": name.language, "name": name.text, "display": name.display} for name in food.names],
        "portions": [{"unit": portion.unit, "grams": portion.grams, "origin": portion.origin} for portion in food.portions],
    })


def _camel(values: dict) -> dict:
    return {_camel_case(key): value for key, value in values.items()}


def _camel_case(snake: str) -> str:
    head, *tail = snake.split("_")
    return head + "".join(part.capitalize() for part in tail)


def _without_empty(values: dict) -> dict:
    """Drops absent and false-y optional fields, which keeps the shipped files small and diffs readable."""
    return {_camel_case(key): value for key, value in values.items() if value not in (None, [], False)}


def _encode(document: dict) -> bytes:
    return (json.dumps(document, ensure_ascii=False, sort_keys=True, indent=1) + "\n").encode("utf-8")
