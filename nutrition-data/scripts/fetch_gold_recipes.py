"""Writes local/gold-recipes.tsv: Chefkoch recipes with kcal per serving, to check the calculator. Never committed.

    ../.venv/bin/python fetch_gold_recipes.py

Per dish, the most-voted recipe stating calories and serving at least two (single servings of cakes state a
piece). Only servings, kcal and ingredient lines are kept. Chefkoch values are estimates too: a sanity bound.
"""

import csv
import json
import urllib.parse
import urllib.request

from cookpal_nutrition import paths

DISHES = [
    "Pfannkuchen", "Spaghetti Bolognese", "Kartoffelsalat", "Rührkuchen", "Gulasch", "Linsensuppe",
    "Pizzateig", "Käsekuchen", "Chili con Carne", "Lasagne", "Kartoffelgratin", "Waffeln",
    "Hackbraten", "Frikadellen", "Rinderrouladen", "Hühnerfrikassee", "Kaiserschmarrn", "Apfelkuchen",
    "Zwiebelkuchen", "Erbsensuppe", "Kürbissuppe", "Tomatensoße", "Risotto", "Gemüsecurry",
    "Bratkartoffeln", "Semmelknödel", "Spätzle", "Milchreis", "Grießbrei", "Quarkauflauf",
    "Nudelauflauf", "Moussaka", "Ratatouille", "Hähnchen Curry", "Kartoffelpuffer", "Marmorkuchen",
    "Brownies", "Bananenbrot", "Pesto", "Gazpacho", "Tzatziki", "Guacamole", "Hummus",
    "Couscous Salat", "Linsensalat", "Schokoladenkuchen", "Zucchinipuffer", "Flammkuchen",
    "Rotkohl", "Porridge", "Shakshuka", "Chili sin Carne", "Gemüselasagne", "Schupfnudeln",
]
API = "https://api.chefkoch.de/v2/recipes"
HEADERS = {"User-Agent": "cookpal-nutrition-data/1 (gold set for the nutrition calculator)"}
CANDIDATES_PER_DISH = 10
FEWEST_SERVINGS = 2
COLUMNS_NOTE = ("# Gold recipes for the nutrition calculator, from Chefkoch (see scripts/fetch_gold_recipes.py).\n"
                "# recipe rows: recipe, id, servings, reference kcal per serving\n"
                "# line rows:   line, recipe id, amount (empty when none), unit, ingredient name\n")


def main() -> None:
    rows = []
    taken: set[str] = set()
    for dish in DISHES:
        recipe = _most_voted_with_calories(dish, taken)
        if recipe is None:
            print(f"{dish}: no recipe with calories")
            continue
        taken.add(recipe["id"])
        detail = _get(f"{API}/{recipe['id']}")
        if (detail.get("servings") or 0) < FEWEST_SERVINGS:
            print(f"{dish}: {detail['title']} serves {detail.get('servings')}, left out")
            continue
        lines = [ingredient for group in detail.get("ingredientGroups") or [] for ingredient in group.get("ingredients") or []]
        rows.append(["recipe", recipe["id"], detail["servings"], recipe["nutrition"]["kCalories"]])
        for line in lines:
            amount = line.get("amount") or 0
            rows.append(["line", recipe["id"], f"{amount:g}" if amount else "", line.get("unit") or "", line["name"]])
        print(f"{dish}: {detail['title']} ({len(lines)} lines, {recipe['nutrition']['kCalories']} kcal)")

    target = paths.LOCAL / "gold-recipes.tsv"
    with target.open("w", newline="", encoding="utf-8") as file:
        file.write(COLUMNS_NOTE)
        csv.writer(file, delimiter="\t", lineterminator="\n").writerows(rows)
    print(f"Wrote {sum(1 for row in rows if row[0] == 'recipe')} recipes to {target}")


def _most_voted_with_calories(dish: str, taken: set[str]) -> dict | None:
    results = _get(f"{API}?{urllib.parse.urlencode({'query': dish, 'limit': CANDIDATES_PER_DISH})}").get("results") or []
    with_calories = [result["recipe"] for result in results
                     if (result["recipe"].get("nutrition") or {}).get("kCalories") and result["recipe"]["id"] not in taken]
    return max(with_calories, key=lambda recipe: (recipe.get("rating") or {}).get("numVotes") or 0, default=None)


def _get(url: str) -> dict:
    with urllib.request.urlopen(urllib.request.Request(url, headers=HEADERS), timeout=30) as response:
        return json.load(response)


if __name__ == "__main__":
    main()
