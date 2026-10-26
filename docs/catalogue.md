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

## Core food and feature facts

The catalogue has two layers, so that it does not grow a column for every feature that knows something about
a few foods.

**Core food** (`catalogue_food` with its names, states and portions) is what every food has or several features
use:

- identity, names and variants (a prepared food is a variant of its unprepared base),
- what the sources state for every food: the nutrients and the water content,
- classification several features read: states, density, portions, negligibility, diet class, aisle.

**Feature facts** are what one feature knows about some foods. Each feature keeps them in a table of its own,
keyed by `catalogue_food_id`, filled by the dataset import and read by that feature alone. `CatalogueFood` does
not know them, and a food without the fact simply has no row.

| Feature | Curation (`catalogue-data/curation/`) | Dataset file | Tables | Code | Endpoint |
|---|---|---|---|---|---|
| Seasonal calendar, seasonal recipe suggestions | `seasons-<region>.yaml`, `storage.yaml` | `seasonal.json` | `seasonal_produce`, `seasonal_produce_month` | `entities.seasonal`, `SeasonalCalendarService` | `GET /api/v1/catalogue/seasons` |
| Bread tool soaker | `soaking.yaml` | `soaking.json` | `soaking_food` | `entities.baking`, `SoakingService` | `GET /api/v1/catalogue/soaking` |
| Shopping list tiles | `shopping-tiles.yaml`, `synonyms/everyday.yaml` (the shown name) | `shopping-tiles.json` | `shopping_tile`, `shopping_tile_name` | `entities.shopping`, `ShoppingTileService` | `GET /api/v1/shopping/vocabulary` |

Food icons stay on `catalogue_food`: the shopping list and the seasonal calendar both show them.

### Where a new attribute goes

On the core food only when the sources state it for (nearly) every food, or when several features use it.
Everything else is a feature fact. Neither a column on `catalogue_food` for one feature nor a generic key/value or
JSON column of facts: the first makes every feature edit the food, the second loses types and constraints.

### How a release brings its facts

The catalogue build writes each feature's facts to its own dataset file, keyed by catalogue key and for bases
only (`catalogue-data/scripts/cookpal_catalogue/feature_facts.py`); the checksum covers these files too. When the
server imports a release, `CatalogueDatasetApplier` first applies the foods, then hands them to every
`CatalogueFacts` implementation in the same transaction. Each feature's service implements it: it reads its file
with `CatalogueDatasetReader.read`, replaces all its rows with the release's, and adds a line to the import report.
A fact for a food the release lacks is skipped and counted in that line.

### Adding feature facts

1. A curation file in `catalogue-data/curation/`, loaded in `curation.py`.
2. A function in `feature_facts.py` that checks keys (bases only, stale keys reported) and its dataset file in
   `CONTENT_FILES` of `dataset.py`. Rebuild with `build_dataset.py`.
3. A Flyway migration: a table keyed by `catalogue_food_id`, `ON DELETE CASCADE` to `catalogue_food`, and the same
   cascade as `@OnDelete` on the entity (integration tests build their schema from the entities).
4. An entity and repository in the feature's package; its service implements `CatalogueFacts`.
5. The feature's endpoint, with its rate limit tier ([rate-limits.md](rate-limits.md)). If the kitchen tools read it
   without an account, add its path to `CataloguePaths.TOOL_PATHS` and answer with `CatalogueResponses.RELEASE_CACHE`.
6. Tests: the build step (pytest), the import (`CatalogueDatasetImportIntegrationTest`) and the endpoint.
