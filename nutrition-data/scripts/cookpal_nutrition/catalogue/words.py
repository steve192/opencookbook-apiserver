"""State words from the lexicons, split by what they say about a food."""

from collections import defaultdict

from cookpal_nutrition import names
from cookpal_nutrition.catalogue.model import Food
from cookpal_nutrition.reference import Reference


class StateWords:

    def __init__(self, reference: Reference):
        self._preparation: dict[str, dict[str, set[str]]] = defaultdict(lambda: defaultdict(set))
        self._processing: dict[str, dict[str, set[str]]] = defaultdict(lambda: defaultdict(set))
        for language in reference.lexicons:
            for word, state in reference.state_words(language).items():
                kind = self._preparation if reference.states[state].kind == "PREPARATION" else self._processing
                kind[language][state].add(word)
        self._contexts = {language: set(lexicon.preparation_context) for language, lexicon in reference.lexicons.items()}
        self.unprepared = {state for state, spec in reference.states.items() if spec.unprepared}
        self.preparations = {state for state, spec in reference.states.items() if spec.kind == "PREPARATION"}

    def preparations_named(self, description: str, language: str) -> set[str]:
        """Preparation states a description names, not counting the unprepared state ("roh")."""
        return self._named(description, self._preparation[language]) - self.unprepared

    def processings_named(self, description: str, language: str) -> set[str]:
        return self._named(description, self._processing[language])

    def is_prepared(self, food: Food) -> bool:
        return bool(set(food.states) & self.preparations)

    def names_from_description(self, food: Food, language: str) -> list[str]:
        """Names a food's description in one language yields; see names.derive."""
        preparation_words = {word for words in self._preparation[language].values() for word in words}
        context = self._contexts.get(language, set()) if self.is_prepared(food) else set()
        return names.derive(food.source_names[language], preparation_words, context)

    @staticmethod
    def _named(description: str, words_by_state: dict[str, set[str]]) -> set[str]:
        return {state for state, words in words_by_state.items() if names.contains_word(description, words)}
