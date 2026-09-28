"""Names derived from source descriptions for uncurated foods.

Preparation words are removed (states carry them); processing words stay ("Tomate getrocknet").
Leading slashes list synonyms ("Dorsch/Kabeljau, tiefgefroren"); slashes in brackets do not.
A bracketed word is a synonym when it shares a word's start or end ("Lachs geräuchert (Räucherlachs)").
"""

import re

# German adjective endings, so "gekochte" is found as "gekocht". Mirrors the light stemming the
# runtime matcher applies to state words.
_INFLECTIONS = ("", "e", "er", "es", "en", "em")

_WORD = re.compile(r"[\w'-]+", re.UNICODE)
_BRACKETED = re.compile(r"\(([^()]*)\)")
_LETTERS = re.compile(r"[^\W\d_]+(-[^\W\d_]+)*", re.UNICODE)
# How many letters a bracketed word must share with the start or end of a word of the name.
_SHARED_EDGE = 4


def derive(description: str, preparation_words: set[str], context_phrases: set[str] = frozenset()) -> list[str]:
    """Most complete first; empty when only preparation is left.

    Pass context phrases ("ohne Fett") only for prepared foods: for unprepared ones they describe the food.
    """
    name = _tidy(_without_words(_without_phrases(description, context_phrases), preparation_words))
    lead, separator, rest = name.partition(",")
    if "/" in lead and "(" not in lead:
        names = [_tidy(alternative + separator + rest) for alternative in lead.split("/")]
    else:
        names = [name]
    names += _bracketed_synonyms(name)
    return list(dict.fromkeys(candidate for candidate in names if candidate))


def _bracketed_synonyms(name: str) -> list[str]:
    words = [word.lower() for word in _WORD.findall(_BRACKETED.sub(" ", name)) if len(word) >= _SHARED_EDGE]
    synonyms = []
    for group in _BRACKETED.findall(name):
        for alternative in (part.strip() for part in group.split("/")):
            if len(alternative) >= _SHARED_EDGE and _LETTERS.fullmatch(alternative) \
                    and any(_shares_edge(alternative.lower(), word) for word in words):
                synonyms.append(alternative)
    return synonyms


def _shares_edge(first: str, second: str) -> bool:
    return first[:_SHARED_EDGE] == second[:_SHARED_EDGE] or first[-_SHARED_EDGE:] == second[-_SHARED_EDGE:]


def contains_word(description: str, words: set[str]) -> bool:
    return any(_is_one_of(match.group(0), words) for match in _WORD.finditer(description))


def _without_phrases(description: str, phrases: set[str]) -> str:
    for phrase in sorted(phrases, key=len, reverse=True):
        description = re.sub(rf"(?<!\w){re.escape(phrase)}(?!\w)", "", description, flags=re.IGNORECASE)
    return description


def _without_words(description: str, words: set[str]) -> str:
    return _WORD.sub(lambda match: "" if _is_one_of(match.group(0), words) else match.group(0), description)


def _is_one_of(word: str, words: set[str]) -> bool:
    lower = word.lower()
    return any(lower == candidate + ending for candidate in words for ending in _INFLECTIONS)


def _tidy(text: str) -> str:
    text = re.sub(r"\(\s*\)", "", text)
    text = re.sub(r"\s+,", ",", text)
    text = re.sub(r",(\s*,)+", ",", text)
    text = re.sub(r"\s{2,}", " ", text)
    return text.strip(" ,")
