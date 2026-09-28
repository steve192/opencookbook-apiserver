# Food catalogue

The food catalogue behind nutrition estimation, recipe diets and shopping lists. It is imported in the
background whenever a server starts with a dataset it has not imported yet. The dataset in
`src/main/resources/catalogue/` is generated in [`catalogue-data/`](../catalogue-data/README.md) from:

- **Bundeslebensmittelschlüssel (BLS) 4.0**, Max Rubner-Institut (2025), DOI 10.25826/Data20251217-134202-0,
  licensed under [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/). Excerpt; values harmonised, names,
  portion weights and densities added.
- **USDA FoodData Central** (Foundation Foods, SR Legacy), U.S. Department of Agriculture, Agricultural Research
  Service, public domain ([CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/)).

What was changed, and the full attribution, is described in [`catalogue-data/ATTRIBUTION.md`](../catalogue-data/ATTRIBUTION.md).

Shopping list icons are [Fluent Emoji](https://github.com/microsoft/fluentui-emoji) (MIT); the catalogue only
names them, the app renders them from the `@iconify-json/fluent-emoji-flat` package when Metro starts.

The manifest's `attributions` (name, homepage, credit line, license) are what the app's open-source licenses
screen shows as the food data, next to the app's and the server's components
(`GET /api/v1/open-source-components`). Nutrition sheets carry them as well.
