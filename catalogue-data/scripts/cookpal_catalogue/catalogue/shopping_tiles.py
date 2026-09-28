"""Which bases a shopping list offers as a tile, and the icon it shows. Variants have no names to show."""

from cookpal_catalogue.catalogue.model import Food, Report
from cookpal_catalogue.curation import Curation, InvalidCuration
from cookpal_catalogue.reference import Reference


def assign(reference: Reference, curation: Curation, foods: dict[str, Food], report: Report) -> None:
    report.stale("shopping-tiles.yaml", curation.shopping_tiles.keys() - foods.keys())
    for key, icon in sorted(curation.shopping_tiles.items()):
        if icon is not None and icon not in reference.icons:
            raise InvalidCuration(f"shopping-tiles.yaml: {key} shows '{icon}', which is not in fluent-icons.txt")
        food = foods.get(key)
        if food is None:
            continue
        if food.variant_of is not None:
            raise InvalidCuration(f"shopping-tiles.yaml: {key} is a variant; make its base {food.variant_of} the tile")
        food.shopping_tile = True
        food.icon = icon
