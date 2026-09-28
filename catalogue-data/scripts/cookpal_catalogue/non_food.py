"""Things a food shop sells that are no food. Shipped next to the catalogue, so one build owns every name."""

from cookpal_catalogue.catalogue import Food
from cookpal_catalogue.curation import Curation, InvalidCuration, NonFood
from cookpal_catalogue.reference import Reference


def check(reference: Reference, curation: Curation, foods: list[Food]) -> list[NonFood]:
    """The curated items, refused where a name is also a food's: a name must mean one thing."""
    taken = {(name.language, name.text.casefold()): food.key for food in foods for name in food.names}
    for item in curation.non_food:
        if item.icon is not None and item.icon not in reference.icons:
            raise InvalidCuration(f"non-food.yaml: {item.key} shows '{item.icon}', which is not in fluent-icons.txt")
        for language, words in item.names.items():
            for word in words:
                owner = taken.setdefault((language, word.casefold()), item.key)
                if owner != item.key:
                    raise InvalidCuration(f"non-food.yaml: '{word}' of {item.key} is already a name of {owner}")
    return sorted(curation.non_food, key=lambda item: item.key)
