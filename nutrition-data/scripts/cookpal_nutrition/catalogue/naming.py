"""Names for every base food, in every enabled language.

Curated names win, else derived ones; a derived name two foods share belongs to neither. Synonyms follow the
names. A food left nameless in a language falls back to its source description, or to the other language's names
(FDC is English only), with the source code appended if taken. Fallbacks are reported.
"""

import re
import unicodedata
from collections import defaultdict

from cookpal_nutrition.catalogue.model import LANGUAGES, Food, Name, Report
from cookpal_nutrition.catalogue.words import StateWords
from cookpal_nutrition.curation import Curation, InvalidCuration

# Share of the higher energy by which foods alike to the matcher must differ to be reported.
_NOTICEABLE_ENERGY_DIFFERENCE = 0.2


def assign(curation: Curation, foods: dict[str, Food], words: StateWords, report: Report) -> None:
    report.stale("names", curation.names.keys() - foods.keys())
    report.stale("synonyms", curation.synonyms.keys() - foods.keys())
    curated = {key: languages for key, languages in curation.names.items() if key in foods}
    synonyms = _synonyms_of_bases(curation, foods, report)
    owners = _curated_owners([curated, synonyms])
    bases = [food for food in foods.values() if food.variant_of is None]

    derived = {food.key: _derived(food, words) for food in bases if food.key not in curated}
    _drop_shared(derived, owners, report)

    chosen = {food.key: {language: list((curated.get(food.key) or derived[food.key]).get(language) or ())
                         for language in LANGUAGES}
              for food in bases}
    taken = {(language, text.casefold()) for texts in chosen.values() for language in LANGUAGES for text in texts[language]}
    taken |= owners.keys()
    for food in bases:
        texts = chosen[food.key]
        _fill_gaps(food, texts, taken, owners, report)
        for language, extra in synonyms.get(food.key, {}).items():
            present = {text.casefold() for text in texts[language]}
            texts[language].extend(text for text in extra if text.casefold() not in present)
        food.names = [Name(language, text, index == 0) for language in LANGUAGES for index, text in enumerate(texts[language])]
    _report_alike_to_the_matcher(bases, report)


def _report_alike_to_the_matcher(bases: list[Food], report: Report) -> None:
    """Same-state foods of different energy whose names differ only in numbers, brackets, punctuation, case,
    accents or word order. The matcher picks among them at random unless a synonym decides."""
    groups: dict[tuple, list[Food]] = defaultdict(list)
    for food in bases:
        forms = {(name.language, _as_the_matcher_reads(name.text)) for name in food.names}
        for language, words in forms:
            groups[(language, words, tuple(sorted(food.states)))].append(food)
    for (language, words, _), foods in sorted(groups.items()):
        energies = [food.nutrients.energy_kcal for food in foods]
        if len(foods) > 1 and max(energies) > 0 and (max(energies) - min(energies)) / max(energies) > _NOTICEABLE_ENERGY_DIFFERENCE:
            report.add("names alike to the matcher, differing in energy",
                       f"'{' '.join(words)}' ({language}): " + ", ".join(f"{food.key} {food.nutrients.energy_kcal:.0f} kcal"
                                                                         for food in sorted(foods, key=lambda food: food.key)))


def _as_the_matcher_reads(text: str) -> tuple[str, ...]:
    without_remarks = re.sub(r"\([^)]*\)|\[[^]]*]", " ", text)
    folded = unicodedata.normalize("NFKD", without_remarks.casefold())
    letters = "".join(char for char in folded if not unicodedata.combining(char))
    return tuple(sorted(word for word in re.split(r"[^a-zß]+", letters) if word))


def _synonyms_of_bases(curation: Curation, foods: dict[str, Food], report: Report) -> dict[str, dict[str, list[str]]]:
    """Synonyms of base foods. Names belong to bases, so synonyms curated for what is now a variant are reported and ignored."""
    for key in sorted(key for key in curation.synonyms if key in foods and foods[key].variant_of is not None):
        report.add("synonyms of a variant ignored", f"{key} is a variant of {foods[key].variant_of}")
    return {key: languages for key, languages in curation.synonyms.items() if key in foods and foods[key].variant_of is None}


def _curated_owners(curations: list[dict[str, dict[str, list[str]]]]) -> dict[tuple[str, str], str]:
    """Which food each curated name or synonym belongs to. Curation giving one name to two foods is an error in the curation."""
    owners: dict[tuple[str, str], str] = {}
    for curated in curations:
        for key, languages in sorted(curated.items()):
            for language, texts in languages.items():
                if language not in LANGUAGES:
                    raise InvalidCuration(f"names for {key} use language {language}, which is not enabled")
                for text in texts:
                    owner = owners.setdefault((language, text.casefold()), key)
                    if owner != key:
                        raise InvalidCuration(f"curated name '{text}' ({language}) is given to both {owner} and {key}")
    return owners


def _derived(food: Food, words: StateWords) -> dict[str, list[str]]:
    return {language: words.names_from_description(food, language) for language in LANGUAGES if language in food.source_names}


def _drop_shared(derived: dict[str, dict[str, list[str]]], owners: dict[tuple[str, str], str], report: Report) -> None:
    claims: dict[tuple[str, str], set[str]] = defaultdict(set)
    for key, languages in derived.items():
        for language, texts in languages.items():
            for text in texts:
                claims[(language, text.casefold())].add(key)
    for (language, folded), claimants in sorted(claims.items()):
        curated_owner = owners.get((language, folded))
        # A curated owner keeps the name; without one, a name only one food derives stays with it.
        losers = claimants - {curated_owner} if curated_owner else (claimants if len(claimants) > 1 else set())
        if not losers:
            continue
        reason = f"curated for {curated_owner}" if curated_owner else "shared by"
        report.add("derived name dropped", f"'{folded}' ({language}) {reason} {', '.join(sorted(losers))}")
        for key in losers:
            derived[key][language] = [text for text in derived[key][language] if text.casefold() != folded]


def _fill_gaps(food: Food, texts: dict[str, list[str]], taken: set[tuple[str, str]], owners: dict[tuple[str, str], str],
               report: Report) -> None:
    for language in LANGUAGES:
        if texts[language] or language not in food.source_names:
            continue
        texts[language] = [_unique(food.source_names[language], language, taken, owners, food)]
        report.add("full source description used as name", f"{food.key} ({language}): {texts[language][0]}")
    for language in LANGUAGES:
        if texts[language]:
            continue
        other = next(texts[other] for other in LANGUAGES if texts[other])
        texts[language] = [_unique(text, language, taken, owners, food) for text in other]
        report.add(f"no {language} name, other language used", f"{food.key}: {food.source_name}")


def _unique(text: str, language: str, taken: set[tuple[str, str]], owners: dict[tuple[str, str], str], food: Food) -> str:
    """The text itself if no other food carries it yet, else with the source code appended. Claims the result."""
    key = (language, text.casefold())
    if key in taken and owners.get(key) != food.key:
        text = f"{text} ({food.source_type} {food.source_code})"
    taken.add((language, text.casefold()))
    return text
