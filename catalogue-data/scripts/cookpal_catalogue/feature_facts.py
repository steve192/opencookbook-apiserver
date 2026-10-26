"""What one feature knows about some catalogue foods, shipped beside the catalogue rather than on its foods.

The food records hold what every food has. A fact only one feature needs for a few foods gets a dataset file of
its own, keyed by catalogue key, which the server imports into that feature's table; see docs/catalogue.md.
Facts belong to bases: a variant is found through its base.
"""

from dataclasses import dataclass

from cookpal_catalogue.catalogue import LANGUAGES, Food, Report
from cookpal_catalogue.catalogue.model import curated_bases
from cookpal_catalogue.curation import Curation, InvalidCuration, Storage


@dataclass(frozen=True)
class SeasonalProduce:
    """A fruit or vegetable of the seasonal calendar: when it grows per region, and how it keeps at home."""
    key: str
    category: str
    # Region ("DE") -> month 1..12 -> growing methods
    seasons: dict[str, dict[int, tuple[str, ...]]]
    storage: Storage | None


@dataclass(frozen=True)
class FeatureFacts:
    seasonal: list[SeasonalProduce]
    # Catalogue key -> grams of water a gram soaks up in a soaker, for the bread tool
    soaking: dict[str, float]
    # Catalogue key -> language -> the word a shopping list tile shows
    shopping_tiles: dict[str, dict[str, str]]


def build(curation: Curation, foods: list[Food], report: Report) -> FeatureFacts:
    by_key = {food.key: food for food in foods}
    return FeatureFacts(seasonal=_seasonal(curation, by_key, report),
                        soaking=dict(_bases("soaking.yaml", curation.soaking, by_key, report)),
                        shopping_tiles=_shopping_tiles(curation, by_key, report))


def _shopping_tiles(curation: Curation, foods: dict[str, Food], report: Report) -> dict[str, dict[str, str]]:
    """A tile shows the word a shop list uses: the first everyday synonym, else the display name ("Reis poliert")."""
    tiles = {}
    for food in curated_bases("shopping-tiles.yaml", curation.shopping_tiles, foods, report):
        displayed = {name.language: name.text for name in food.names if name.display}
        everyday = curation.synonyms.get(food.key, {})
        names = {language: (everyday.get(language) or [displayed.get(language)])[0] for language in LANGUAGES}
        tiles[food.key] = {language: name[0].upper() + name[1:] for language, name in names.items() if name}
    return tiles


def _seasonal(curation: Curation, foods: dict[str, Food], report: Report) -> list[SeasonalProduce]:
    categories: dict[str, str] = {}
    seasons: dict[str, dict[str, dict[int, tuple[str, ...]]]] = {}
    for region, regional in sorted(curation.seasons.items()):
        file = f"seasons-{region.lower()}.yaml"
        for key, season in _bases(file, regional, foods, report):
            # A food is fruit or vegetable wherever it grows; the region files only repeat it.
            if categories.setdefault(key, season.category) != season.category:
                raise InvalidCuration(f"{file}: {key} is {season.category} here but {categories[key]} "
                                      f"in another region")
            seasons.setdefault(key, {})[region] = season.months
    storage = dict(_bases("storage.yaml", curation.storage, foods, report))
    if storage.keys() - seasons.keys():
        raise InvalidCuration(f"storage.yaml: {sorted(storage.keys() - seasons.keys())} have no season; storage "
                              f"tips are shown by the seasonal calendar only")
    return [SeasonalProduce(key=key, category=categories[key], seasons=seasons[key], storage=storage.get(key))
            for key in sorted(seasons)]


def _bases(file: str, facts: dict, foods: dict[str, Food], report: Report) -> list[tuple[str, object]]:
    return [(food.key, facts[food.key]) for food in curated_bases(file, facts, foods, report)]
