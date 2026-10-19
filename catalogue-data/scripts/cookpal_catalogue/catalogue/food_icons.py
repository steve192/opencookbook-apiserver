"""The icon each base shows. Variants show their base's."""

from cookpal_catalogue.catalogue.model import Food, Report, curated_bases
from cookpal_catalogue.curation import Curation, InvalidCuration
from cookpal_catalogue.reference import Reference


def assign(reference: Reference, curation: Curation, foods: dict[str, Food], report: Report) -> None:
    for key, icon in sorted(curation.food_icons.items()):
        if icon not in reference.icons:
            raise InvalidCuration(f"food-icons.yaml: {key} shows '{icon}', which is not in fluent-icons.txt or own-icons.txt")
    for food in curated_bases("food-icons.yaml", curation.food_icons, foods, report):
        food.icon = curation.food_icons[food.key]
