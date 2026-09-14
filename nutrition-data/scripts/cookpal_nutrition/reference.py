"""Units, states and the per-language lexicons: fixed reference data shipped next to the catalogue."""

from dataclasses import dataclass
from pathlib import Path

import yaml

UNIT_KINDS = ("MASS", "VOLUME", "COUNT", "PINCH", "VAGUE")
STATE_KINDS = ("PREPARATION", "PROCESSING")


@dataclass(frozen=True)
class Unit:
    key: str
    kind: str
    grams: float | None = None
    millilitres: float | None = None
    size_of: str | None = None
    factor: float | None = None
    # COUNT units of containers: what one usually holds, for foods without a portion of their own.
    typical_grams: float | None = None
    typical_millilitres: float | None = None


@dataclass(frozen=True)
class State:
    key: str
    kind: str
    unprepared: bool = False


@dataclass(frozen=True)
class Lexicon:
    language: str
    units: dict[str, tuple[str, ...]]
    states: dict[str, tuple[str, ...]]
    # Phrases that describe a preparation without being one ("ohne Fett", "Pfanne").
    preparation_context: tuple[str, ...] = ()
    # Phrases saying an ingredient is only used a little, for greasing, dusting or garnish ("für die Form").
    sparing_uses: tuple[str, ...] = ()


@dataclass(frozen=True)
class Reference:
    units: dict[str, Unit]
    states: dict[str, State]
    bls_preparation_digits: dict[str, str]
    lexicons: dict[str, Lexicon]

    def state_words(self, language: str) -> dict[str, str]:
        """Lower-case state word -> state key, for one language."""
        return {word.lower(): state for state, words in self.lexicons[language].states.items() for word in words}


class InvalidReference(ValueError):
    pass


def load(curation: Path) -> Reference:
    units = _units(_read(curation / "units.yaml")["units"])
    states_document = _read(curation / "states.yaml")
    states = _states(states_document["states"])
    digits = {str(digit): state for digit, state in states_document["blsPreparationDigits"].items()}
    _require_known(digits.values(), states, "BLS preparation digit")
    lexicons = {}
    for path in sorted((curation / "lexicon").glob("*.yaml")):
        lexicon = _lexicon(_read(path))
        _require_known(lexicon.units, units, f"unit in {path.name}")
        _require_known(lexicon.states, states, f"state in {path.name}")
        lexicons[lexicon.language] = lexicon
    reference = Reference(units=units, states=states, bls_preparation_digits=digits, lexicons=lexicons)
    _require_unambiguous_words(reference)
    return reference


def _units(document: dict) -> dict[str, Unit]:
    units = {key: Unit(key=key, kind=spec["kind"], grams=spec.get("grams"), millilitres=spec.get("millilitres"),
                       size_of=spec.get("sizeOf"), factor=spec.get("factor"),
                       typical_grams=spec.get("typicalGrams"), typical_millilitres=spec.get("typicalMillilitres"))
             for key, spec in document.items()}
    for unit in units.values():
        if unit.kind not in UNIT_KINDS:
            raise InvalidReference(f"unit {unit.key} has unknown kind {unit.kind}")
        required = {"MASS": unit.grams, "VOLUME": unit.millilitres, "PINCH": unit.grams}
        if unit.kind in required and not required[unit.kind]:
            raise InvalidReference(f"{unit.kind} unit {unit.key} needs its amount")
        typical = [size for size in (unit.typical_grams, unit.typical_millilitres) if size is not None]
        if typical and (unit.kind != "COUNT" or unit.size_of is not None or len(typical) > 1 or typical[0] <= 0):
            raise InvalidReference(f"unit {unit.key}: a typical size belongs to a plain COUNT unit, in grams or millilitres")
        if unit.size_of is not None:
            base = units.get(unit.size_of)
            if unit.kind != "COUNT" or base is None or base.kind != "COUNT" or base.size_of or not unit.factor:
                raise InvalidReference(f"unit {unit.key} must be a COUNT scaling a plain COUNT unit by a factor")
    return units


def _states(document: dict) -> dict[str, State]:
    states = {key: State(key=key, kind=spec["kind"], unprepared=bool(spec.get("unprepared", False)))
              for key, spec in document.items()}
    for state in states.values():
        if state.kind not in STATE_KINDS:
            raise InvalidReference(f"state {state.key} has unknown kind {state.kind}")
    return states


def _lexicon(document: dict) -> Lexicon:
    return Lexicon(
        language=document["language"],
        units={key: tuple(str(word) for word in words) for key, words in document["units"].items()},
        states={key: tuple(str(word) for word in words) for key, words in document["states"].items()},
        preparation_context=tuple(str(phrase) for phrase in document.get("preparationContext") or ()),
        sparing_uses=tuple(str(phrase) for phrase in document.get("sparingUses") or ()),
    )


def _require_known(keys, known: dict, what: str) -> None:
    unknown = sorted(set(keys) - known.keys())
    if unknown:
        raise InvalidReference(f"unknown {what}: {unknown}")


def _require_unambiguous_words(reference: Reference) -> None:
    """A word may appear in several languages, but must mean the same thing in all of them."""
    for vocabulary in ("units", "states"):
        meanings: dict[str, str] = {}
        for lexicon in reference.lexicons.values():
            for key, words in getattr(lexicon, vocabulary).items():
                for word in words:
                    known = meanings.setdefault(word.lower(), key)
                    if known != key:
                        raise InvalidReference(f"{vocabulary[:-1]} word '{word}' means both {known} and {key}")


def _read(path: Path) -> dict:
    with path.open(encoding="utf-8") as file:
        return yaml.safe_load(file)
