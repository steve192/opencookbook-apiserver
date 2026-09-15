# Nutrition data

Nutrition estimation is turned off by default; set `opencookbook.nutrition.enabled` to `true` to import the
shipped dataset and use it. The dataset in `src/main/resources/nutrition/` is generated in
[`nutrition-data/`](../nutrition-data/README.md) from:

- **Bundeslebensmittelschlüssel (BLS) 4.0**, Max Rubner-Institut (2025), DOI 10.25826/Data20251217-134202-0,
  licensed under [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/). Excerpt; values harmonised, names,
  portion weights and densities added.
- **USDA FoodData Central** (Foundation Foods, SR Legacy), U.S. Department of Agriculture, Agricultural Research
  Service, public domain ([CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/)).

What was changed, and the full attribution, is described in [`nutrition-data/ATTRIBUTION.md`](../nutrition-data/ATTRIBUTION.md).
