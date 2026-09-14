"""Finds built catalogue foods whose names or description contain every search word.

    ../.venv/bin/python find_foods.py kartoffel geschält
    ../.venv/bin/python find_foods.py --file terms.txt      one search per line
"""

import argparse
import json
import unicodedata
from pathlib import Path

from cookpal_nutrition import paths

MOST_FOODS_PER_SEARCH = 12


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("words", nargs="*")
    parser.add_argument("--file", type=Path)
    arguments = parser.parse_args()
    searches = [line.split() for line in arguments.file.read_text().splitlines() if line.strip()] if arguments.file \
        else [arguments.words]

    foods = json.loads((paths.DATASET / "catalogue.json").read_text())["foods"]
    variants: dict[str, list[dict]] = {}
    for food in foods:
        if food.get("variantOf"):
            variants.setdefault(food["variantOf"], []).append(food)
    bases = [food for food in foods if not food.get("variantOf")]

    for words in searches:
        wanted = [_folded(word) for word in words]
        found = [food for food in bases if all(word in _searchable(food) for word in wanted)]
        found.sort(key=lambda food: len(_display(food)))
        print(f"== {' '.join(words)} ({len(found)})")
        for food in found[:MOST_FOODS_PER_SEARCH]:
            states = ",".join(food.get("states", []))
            print(f"  {food['key']:<14} {food['nutrients']['energyKcal']:>6} kcal  {_display(food)}"
                  f"{'  [' + states + ']' if states else ''}  | {' / '.join(name['name'] for name in food['names'][1:4])}")
            for variant in variants.get(food["key"], []):
                print(f"      {variant['key']:<14} {','.join(variant.get('states', []))}")


def _display(food: dict) -> str:
    return next((name["name"] for name in food["names"] if name["display"]), food["source"]["name"])


def _searchable(food: dict) -> str:
    return _folded(" ".join([food["source"]["name"], *(name["name"] for name in food["names"])]))


def _folded(text: str) -> str:
    decomposed = unicodedata.normalize("NFKD", text.casefold())
    return "".join(char for char in decomposed if not unicodedata.combining(char))


if __name__ == "__main__":
    main()
