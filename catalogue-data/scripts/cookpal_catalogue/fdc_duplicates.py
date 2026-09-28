"""Decides by rule whether an FDC food duplicates a BLS food.

Descriptions are reduced to naming words (qualifiers dropped, plurals folded, US words mapped via
curation/fdc-vocabulary.yaml). A BLS food is a candidate when one name's words contain the other's, or it holds
FDC's leading food word, or a single shared word is the whole of a short name - and energy agrees.
Ties: most shared words, fewest differing, same state, closest energy.
When in doubt a food is new: a missed duplicate is harmless, a wrong one drops a real food.
"""

import re
from collections import defaultdict
from dataclasses import dataclass
from typing import Iterable

# Words about the state a food is in. They do not name it, but tell apart candidates that do.
_STATE_MARKERS = frozenset("""
    raw fresh frozen canned bottled dried dry cooked boiled stewed steamed braised baked roasted grilled fried
    smoked pickled salted unsalted sweetened unsweetened concentrate
""".split())
# Other words about how a food is sold, prepared or graded rather than what it is.
_QUALIFIERS = _STATE_MARKERS | frozenset("""
    deep drained solids liquids undrained shelf stable refrigerated unprepared uncooked prepared commercial regular
    pack packed ready serve heated pasteurized pasteurised ultra heat treated enriched unenriched bleached unbleached
    fortified added vitamin vitamins calcium iron with without no not and or in of from to the a an for all purpose
    type grade large medium small whole plain natural organic sodium salt sugar sugared mature ripe
    min max fat free lean trimmed separable boneless skinless bone skin meat only matter
    pulp seedless peeled seeded destemmed root removed bulb flesh halves pieces diced sliced crushed
    caught farm raised includes include rinsed solid block style links link
""".split())
_WORD = re.compile(r"[a-z]+")

# One shared word identifies a food only when neither name has more than this many naming words.
SHORT_NAME_WORDS = 2


@dataclass(frozen=True)
class EnergyTolerance:
    """How far apart two sources' energy may be for their descriptions to mean the same food."""
    share: float
    kcal: float

    def allows(self, fdc_kcal: float | None, bls_kcal: float) -> bool:
        return fdc_kcal is None or abs(fdc_kcal - bls_kcal) <= max(self.kcal, bls_kcal * self.share)


# For deciding that a food is not new. Tight: a wrong duplicate removes a food.
SAME_FOOD = EnergyTolerance(share=0.3, kcal=20.0)
# For carrying portion weights over. Looser: a clove of garlic weighs the same whatever the tables
# say about its energy, while a dried powder and the fresh food still stay apart.
SAME_KIND = EnergyTolerance(share=1.0, kcal=40.0)


@dataclass(frozen=True)
class Candidate:
    code: str
    words: frozenset[str]
    markers: frozenset[str]
    energy_kcal: float


@dataclass(frozen=True)
class Described:
    """An FDC food as the rule sees it."""
    words: frozenset[str]
    lead: frozenset[str]
    markers: frozenset[str]
    energy_kcal: float | None


@dataclass(frozen=True)
class Decision:
    duplicate_of: str | None
    reason: str


class Vocabulary:
    """Reads descriptions of either source into naming words, in the BLS's vocabulary."""

    def __init__(self, synonyms: dict[str, str], implied: dict[str, str], ignored_phrases: Iterable[str]):
        self._synonyms = {_singular(american): _singular(british) for american, british in synonyms.items()}
        self._implied = {_singular(word): _singular(implied_word) for word, implied_word in implied.items()}
        self._ignored = sorted((phrase.lower() for phrase in ignored_phrases), key=len, reverse=True)

    def candidate(self, code: str, description: str, energy_kcal: float) -> Candidate:
        return Candidate(code=code, words=self.naming_words(description), markers=self._markers(description),
                         energy_kcal=energy_kcal)

    def describe(self, description: str, energy_kcal: float | None) -> Described:
        words = self.naming_words(description)
        implied = frozenset(self._implied[word] for word in words if word in self._implied)
        return Described(words=words | implied, lead=self._lead(description), markers=self._markers(description),
                         energy_kcal=energy_kcal)

    def naming_words(self, description: str) -> frozenset[str]:
        return frozenset(self._synonyms.get(word, word) for word in self._words(description, excluding=_QUALIFIERS))

    def _lead(self, description: str) -> frozenset[str]:
        """What FDC leads with. A one-word first phrase is often a category ("Fish, haddock"), so the next one counts too."""
        phrases = description.split(",")
        lead = self.naming_words(phrases[0])
        return lead | self.naming_words(phrases[1]) if len(lead) == 1 and len(phrases) > 1 else lead

    def _markers(self, description: str) -> frozenset[str]:
        return frozenset(word for word in self._words(description) if word in _STATE_MARKERS)

    def _words(self, description: str, excluding: frozenset[str] = frozenset()) -> list[str]:
        """Singular words of a description; a word listed in `excluding` in either form is left out."""
        text = description.lower()
        for phrase in self._ignored:
            text = text.replace(phrase, " ")
        singulars = ((word, _singular(word)) for word in _WORD.findall(text) if len(word) > 1)
        return [singular for word, singular in singulars if word not in excluding and singular not in excluding]


class CandidateIndex:
    """BLS candidates by naming word. A candidate must share a word with a food to name the same food."""

    def __init__(self, candidates: Iterable[Candidate]):
        self._by_word: dict[str, list[Candidate]] = defaultdict(list)
        for candidate in candidates:
            for word in candidate.words:
                self._by_word[word].append(candidate)

    def sharing_a_word_with(self, words: frozenset[str]) -> list[Candidate]:
        return list({candidate.code: candidate for word in words for candidate in self._by_word.get(word, ())}.values())


def decide(food: Described, candidates: CandidateIndex, tolerance: EnergyTolerance = SAME_FOOD) -> Decision:
    words = food.words
    if not words:
        return Decision(None, "no naming words")
    named = [candidate for candidate in candidates.sharing_a_word_with(words) if _names_the_same_food(food, candidate)]
    if not named:
        return Decision(None, "no BLS food named by the same words")
    close = [candidate for candidate in named if tolerance.allows(food.energy_kcal, candidate.energy_kcal)]
    if not close:
        return Decision(None, "BLS foods named by the same words differ in energy")
    best = min(close, key=lambda candidate: (
        -len(words & candidate.words),
        len(words ^ candidate.words),
        len(food.markers ^ candidate.markers),
        abs((food.energy_kcal or candidate.energy_kcal) - candidate.energy_kcal),
        candidate.code))
    return Decision(best.code, f"shares {len(words & best.words)} naming words")


def _names_the_same_food(food: Described, candidate: Candidate) -> bool:
    words = food.words
    if not candidate.words or not (candidate.words <= words or words <= candidate.words):
        return False
    if not food.lead & candidate.words:
        return False
    shared = len(words & candidate.words)
    return shared >= 2 or max(len(words), len(candidate.words)) <= SHORT_NAME_WORDS


def _singular(word: str) -> str:
    if word.endswith("ies") and len(word) > 4:
        return word[:-3] + "y"
    if re.search(r"(ch|sh|x|ss|o)es$", word) and len(word) > 4:
        return word[:-2]
    if word.endswith("s") and not word.endswith("ss") and len(word) > 3:
        return word[:-1]
    return word
