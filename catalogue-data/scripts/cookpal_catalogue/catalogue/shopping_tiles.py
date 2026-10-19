"""Which bases a shopping list offers as a tile. Variants have no names to show."""

from cookpal_catalogue.catalogue.model import Food, Report, curated_bases
from cookpal_catalogue.curation import Curation


def assign(curation: Curation, foods: dict[str, Food], report: Report) -> None:
    for food in curated_bases("shopping-tiles.yaml", curation.shopping_tiles, foods, report):
        food.shopping_tile = True
