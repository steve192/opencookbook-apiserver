# Nutrition Estimation — Architecture & Implementation Plan

**Status:** Approved, ready for phase 0 · **Date:** 2026-09-13 · **First usecase:** nutrition facts per serving on a recipe

Repos affected: `opencookbook-apiserver` (backend, admin UI, new `nutrition-data/` directory) · `opencookbook-frontend` (recipe screen, link correction) · `cookpal-ml-subsystem` (optional, phase 6 only). `opencookbook-proxy` and `opencookbook` (compose) are **not** affected.

---

## 1. What we are building

Every recipe shows estimated nutrition facts: energy (kcal and kJ), fat, saturated fat, carbohydrates, sugar, fibre, protein and salt, per serving, or for the whole recipe when it has no servings. The estimate is built bottom-up:

1. Each user ingredient is linked to a **catalogue food**. The catalogue contains every food from the Bundeslebensmittelschlüssel (BLS) and USDA FoodData Central (FDC), without duplicates and without prepared dishes nobody would list as an ingredient. Values are harmonised to one definition.
2. Each recipe line's amount and unit are converted to grams.
3. The lines are summed.

When a line that matters cannot be linked or converted, or its link is uncertain, the recipe says so next to the numbers, and the owner can fix the link in one tap.

The catalogue ships inside the apiserver jar and changes only with apiserver releases. Nothing at runtime calls an LLM. LLMs are a development tool: an LLM on a subscription generates metadata that is reviewed, committed and shipped. The only runtime model allowed is a small self-hosted one in the ML subsystem (phase 6), and every feature works without it.

---

## 2. Decisions locked

| # | Decision | Choice | Why |
|---|---|---|---|
| 1 | Data model | Separate catalogue tables. `ingredient` becomes purely user-owned and holds only a link to the catalogue | Catalogue rows and user rows have nothing in common; mixing them caused `aliasFor` hacks, the transaction bug, and recipe lines pointing at public rows |
| 2 | Nutrient source | BLS 4.0 first; FDC Foundation Foods for foods BLS does not have; FDC Foundation and SR Legacy for portion weights | BLS is German, current, bilingual, with provenance per value; FDC fills gaps and has portion weights |
| 2a | Food scope | **All** BLS foods minus prepared dishes that are never an ingredient, plus the FDC **Foundation Foods** that do not duplicate a BLS food (§5.4). SR Legacy contributes portion weights, and foods only from food groups BLS and Foundation Foods lack (`srLegacyCategories`: *Spices and Herbs*) plus single foods listed in `inclusion.yaml` (fresh coriander, lemongrass, pesto, baking soda) | The catalogue does not depend on what one instance's users happen to cook. SR Legacy (frozen 2018) as a whole would add ~7,300 foods, mostly brand products and US meat cuts overlapping BLS, which cannot be deduplicated and reviewed reliably (decided 2026-09-13). BLS 4.0 has only three spices, so herbs and spices would otherwise always be unlinked (decided 2026-09-13) |
| 2b | Production data | Used only to **test** (production gold set, coverage report), never to decide which foods exist, and **never committed** to any repository | The catalogue must not depend on one instance; user-entered text stays out of git |
| 3 | Harmonisation | All values follow the EU labelling definitions (Reg. 1169/2011): available carbohydrates, protein N×6.25, energy from EU conversion factors | BLS already does; FDC uses different definitions and would otherwise read systematically higher (§5.3) |
| 4 | LLM-written values | Never for nutrients. LLMs may propose names, translations and corrections to rule decisions, reviewed as data | Plausible invented numbers are worse than a visible gap |
| 5 | Shipped data | Read-only inside an instance. Corrections go into `curation/` and ship with a release. Admins may add names and custom foods | One source of truth, no per-field lock flags |
| 5a | Source releases | Imported by one command without curation; generic rules decide, a report lists their decisions, curation only corrects (decided 2026-09-13) | New BLS/FDC releases must not require manual filtering |
| 6 | Shipping mechanism | Schema by Flyway; catalogue by a versioned dataset in the jar, imported after startup | Migrations are immutable and unreviewable for thousands of rows; see §6.1 |
| 7 | Dataset updates | Only with apiserver releases | Dataset, matcher and code are tested together |
| 8 | Fixed lexicons | Units, state words and stopwords load from the classpath into memory, not into tables | Static reference data that changes only with releases |
| 9 | Descriptors | Language-neutral state concepts; languages only contribute word lists (§7.2) | Adding a language must be a data change |
| 10 | Matching | Lucene (Java library, in-process) plus a feature-based confidence calibrated on the local production gold set | Fast, testable on H2, no extra service |
| 11 | Embeddings | Optional stage in the ML subsystem, only if it measurably improves the gold sets | Must not be required; must be proven |
| 12 | Link provenance | `AUTO` (matcher, incl. relink runs), `USER`, `ADMIN` (single explicit admin link) | Relinks never overwrite a person's decision; bulk accepts do not freeze links |
| 13 | Admin decisions | Positive decisions become catalogue names; negative ones become persisted rules | A rejected proposal must not come back every run |
| 14 | Calculation | Computed on read, never stored. Summary in the recipe response, line details in a separate endpoint | No stale totals; lean recipe lists |
| 15 | Warnings | By impact, not "any line imperfect" | Otherwise almost every recipe warns |
| 16 | No servings | Show values for the whole recipe | — |
| 17 | App v1 | Shows every nutrient the catalogue has | — |
| 18 | Ship gate | §12 criteria must hold on production data before the app shows values | — |
| 19 | Feature flag | `opencookbook.nutrition.enabled`, **default `false`**, exposed to the app as `nutritionEnabled` in the instance info (like `sharingEnabled`) | Opt-in while the feature is new |
| 20 | Languages | German and English in v1 | Both come with BLS; more languages are a data change (§7.2) |
| 21 | Search library | Lucene | Plain Java library in-process; measured memory in §7.1 |

---

## 3. Findings in the current code

Verified on `improved-error-handling`, 2026-09-13.

| Finding | Consequence for this plan |
|---|---|
| Public and private ingredients share `ingredient`; public rows carry nutrients and alternative names, private rows `alias_for_id` | Replaced by the catalogue split (§4) |
| `IngredientMatcher` (intuit fuzzy-matcher): an exact hit scores 1.00 with one name, 0.57 with seven, 0.53 with fifteen against a 0.5 threshold; "mehligkochende Kartoffeln", "brauner Zucker", "Milch 3,5%", "Öl" do not match | Replaced (§7) |
| `populateNutrients` sets copied nutrients on managed entities inside `@Transactional`, so they are flushed into private rows | Disappears by construction: user ingredients have no nutrient columns after the split, and the calculator is read-only |
| **Security:** `RecipeController` passes client-supplied `IngredientNeed`/`Ingredient` entities through, and `RecipeService` keeps any ingredient `id` it is given. `hasPermissionForIngredient` exists but is never called, so a recipe can reference another user's ingredient | Fixed in phase 0. Ingredient-need ids get the same check |
| `GET /api/v1/ingredients` returns public plus own ingredients with ids; the app sends a chosen id back when saving | After the split it returns the user's **own** ingredients only, and an ingredient id sent with a recipe is ignored (the name decides). Catalogue suggestions come from the search endpoint of phase 2 (§9.3). The app already handles id-less ingredients (`RecipeWizardScreen` keys by name), so released app versions keep working |
| Admin "From JSON" dialog asks an LLM for nutrient values | Removed (decision 4) |
| `Ingredient.additionalInfo` is set by importers per recipe line but stored per ingredient, and never shown by the app | Not part of this plan (§13), but the split must not lose it |
| `src/main/resources/ingredientsources/` (USDA 2023, 280 MB, gitignored, unused) | Deleted |
| `ChefkochImporter` receives `kCalories` and drops it | Used only as an evaluation reference (§12) |
| `docs/ml-subsystem-plan.md` listed calorie detection as a future ML job type | Updated (2026-09-13) to point here |

---

## 4. Data model

### 4.1 Target schema

```
catalogue_food
  id                    bigint PK                    (catalogue_food_seq)
  catalogue_key         varchar NOT NULL UNIQUE      "potato", "tomato-dried", "custom-7f3a…"
  origin                varchar NOT NULL             DATASET | CUSTOM
  variant_of_id         bigint NULL FK catalogue_food   variants share names with their base; derived from the source family (§5.4)
  retired               boolean NOT NULL             no longer in the dataset, still linkable
  source_type           varchar NULL                 BLS | FDC (null for CUSTOM)
  source_code           varchar NULL                 "K110100"
  source_name           varchar NULL                 "Kartoffel geschält, roh"
  negligible            boolean NOT NULL             water, salt: may lack an amount silently
  density_g_per_ml      real NULL
  energy_kcal, energy_kj, fat, saturated_fat, carbohydrates, sugar, fibre, protein, salt
                        real NULL, per 100 g, harmonised (§5.3)
  created_on, last_change

catalogue_food_state    (catalogue_food_id FK ON DELETE CASCADE, state_key varchar)  PK both
catalogue_food_name     element collection of catalogue_food, no id of its own
  catalogue_food_id FK ON DELETE CASCADE, language_iso_code varchar NOT NULL,
  name varchar NOT NULL, display boolean NOT NULL, origin DATASET | ADMIN
  UNIQUE INDEX (language_iso_code, lower(name))   a name belongs to one food, whatever its capitalisation
catalogue_food_portion  element collection of catalogue_food
  catalogue_food_id FK ON DELETE CASCADE, unit_key varchar NOT NULL, grams real NOT NULL CHECK > 0,
  origin SOURCE | ESTIMATED | ADMIN
  PK (catalogue_food_id, unit_key)
catalogue_name_rule                      persisted negative admin decisions (phase 3)
  id, name varchar NOT NULL, kind NEVER_LINK_TO | NOT_A_FOOD,
  food_id FK NULL (required for NEVER_LINK_TO), created_by, created_on
nutrition_dataset_import
  checksum varchar PK, label, status RUNNING | DONE | FAILED, started_at, finished_at, report text
  the checksum identifies a release's content; inserting the row is how instances agree who imports

ingredient                               user-owned only
  id, name varchar NOT NULL, owner_user_id bigint NOT NULL FK,
  additional_info varchar NULL           unchanged, see §13
  catalogue_food_id bigint NULL FK catalogue_food ON DELETE RESTRICT
  link_source varchar NULL               AUTO | USER | ADMIN; null = never matched
  link_confidence real NULL
  link_matcher_version int NULL
  link_run_id bigint NULL FK ingredient_relink_run ON DELETE SET NULL   (phase 3)
  linked_at timestamp NULL
  excluded_from_nutrition boolean NOT NULL DEFAULT false
  created_on, last_change
  UNIQUE (owner_user_id, name)

ingredient_relink_run, ingredient_relink_proposal     §8.1
```

Removed entirely: `ingredient.is_public_ingredient`, `ingredient.alias_for_id`, the seven `ingredient.nutrients_*` columns, table `ingredient_alternative_names` and sequence `ingredient_alternativenames_seq`.

**Consistency rules:**
- A display name is required per enabled language (`de`, `en`), exactly one each. The service and the dataset build enforce this, because H2 has no partial unique indexes.
- A `USER` link with `excluded_from_nutrition = true` has `catalogue_food_id = null`.
- Catalogue foods are never deleted while referenced (`RESTRICT`); dataset foods are retired instead, and custom foods are merged (§8.4).

### 4.2 Migration `V17__catalogue_split.sql`

One Flyway migration, so it runs in one Postgres transaction and either fully applies or leaves V16 untouched. Steps:

1. **Guards** (a `DO` block raising an exception, which aborts the migration):
   - a public ingredient with an owner
   - a private ingredient without one
   - `ingredient_alternative_names` rows on private ingredients (the API never writes them; if any exist, stop and decide)
2. **Create** the catalogue tables and sequences.
3. **Public ingredients → `catalogue_food`**, `origin = CUSTOM`, `catalogue_key = 'legacy-' || id`, nutrients copied, `energy_kj` and `fibre` null. A temporary column `legacy_ingredient_id` holds the mapping.
4. **Names:**
   - each public ingredient's name becomes a display name; language `de`, since legacy names are German and the admin can correct this
   - each alternative name becomes a non-display name, `origin = ADMIN`
   - duplicates by (language, name) are resolved by keeping the lowest food id; each dropped name is written to the migration's `RAISE NOTICE` log
5. **Add link columns** to `ingredient`.
6. **Private ingredients with an alias:** `catalogue_food_id` from the mapping, `link_source = AUTO`, `link_matcher_version = 0` (legacy), confidence null.
7. **Recipe lines pointing at a public ingredient:**
   - For each distinct (recipe owner, public ingredient) found via `recipe_needed_ingredients` → `recipe.owner_user_id`: reuse the owner's private ingredient with the same name, or insert one linked to the mapped catalogue food (`AUTO`, version 0).
   - Repoint `ingredient_need.ingredient_id`.
   - Lines not attached to any recipe are orphans left behind by earlier bugs; they are deleted.
8. **Duplicate private ingredients** per (owner, name): repoint needs to the lowest id and delete the rest.
9. **Delete public ingredients**, drop `alias_for_id` (and its FK), `is_public_ingredient`, the nutrient columns, `ingredient_alternative_names` and its sequence. Set `name` and `owner_user_id` `NOT NULL`, add `UNIQUE (owner_user_id, name)`.
10. **Drop** the temporary `legacy_ingredient_id`.

**Migration test** (`CatalogueSplitMigrationIntegrationTest`, Testcontainers Postgres, same pattern as `MlJobMigrationIntegrationTest`):
- Flyway migrates to target `16`.
- The test seeds public ingredients with alternative names and nutrients, private aliased and unaliased ingredients, recipe lines on public ingredients from two users, duplicate private names, and an orphan line.
- Flyway migrates to latest.
- Asserts:
  - every recipe line resolves to an ingredient owned by its recipe's owner
  - links preserved
  - names preserved
  - no dropped column or table remains (checked through `information_schema`)
  - Hibernate validates the schema (`ddl-auto=validate`)

**Before production:** run the guard queries and the step 7/8 counts on a copy of the production database and record the numbers in the PR.

### 4.3 Legacy custom foods after the first dataset import

The importer merges a `legacy-*` custom food into a dataset food automatically when one of its names equals a dataset name in the same language after normalisation. The merge repoints ingredients (keeping `link_source`), keeps non-conflicting `ADMIN` names, and deletes the legacy food. Everything else stays a custom food and is listed in the cockpit (§8.2) for the admin to merge or keep.

---

## 5. Data

### 5.1 Layers and repository layout

| Layer | Where | In git | Used by |
|---|---|---|---|
| **Raw downloads**: BLS zip, FDC JSON | `nutrition-data/raw/` | No (gitignored) | The import |
| **Source extracts**: only the fields cookpal uses, values unchanged, plus `provenance.json` (which downloads, sha256) | `nutrition-data/sources/` | Yes, a few MB | The build |
| **Cookpal dataset**: harmonised, combined, named, with portions | `src/main/resources/nutrition/` | Yes, generated | Every instance |

**Importing a new release is one command** (`scripts/import_sources.py`) and needs no curation:
every decision is taken by a rule working on the structure of the sources, and a report lists what
the rules decided (§5.6). `curation/` holds reference data (units, states, lexicons, FDC
vocabulary) and **corrections** to individual rule decisions. A correction referring to a food a
release no longer has is reported and ignored. The build only fails where shipping would be wrong:
the energy recomputation no longer reproduces BLS, or curation contradicts itself.

```
nutrition-data/
  README.md, SOURCES.md, ATTRIBUTION.md
  raw/                               gitignored; newest download of each source is used
  sources/
    bls/foods.csv                    code, names de/en, components
    fdc/foods.csv, fdc/portions.csv  Foundation + SR Legacy
    provenance.json                  downloads used, with release and sha256
  curation/
    units.yaml, states.yaml, lexicon/<lang>.yaml   reference data, shipped
    fdc-vocabulary.yaml              American -> British words, implied words, ignored phrases
    inclusion.yaml                   group exclusions; per-food exceptions
    families.yaml                    corrections to variant linking
    fdc-duplicates.yaml              corrections to the FDC duplicate rule
    names/<source>.yaml              curated names, replacing derived ones
    synonyms/*.yaml                  everyday names added to a food's names ("Butter" for "Süßrahmbutter")
    portions.yaml, properties.yaml   corrections to portion weights, density, negligibility
    dataset.yaml                     human-readable label
  gold/                              test sets written for the purpose (§12)
  local/                             gitignored: build report, production exports
  scripts/
    import_sources.py                raw -> sources -> dataset
    build_dataset.py                 sources + curation -> dataset
    find_foods.py                    searches the built catalogue, for curating
    fetch_gold_recipes.py            writes local/gold-recipes.tsv from Chefkoch (§12)
    cookpal_nutrition/               the rules, with tests in scripts/tests
src/main/resources/nutrition/
  manifest.json                      label, checksum, languages, attributions, sources
  catalogue.json, units.json, states.json, lexicons.json
```

Python runs locally only. CI validates the committed dataset with Java tests (§12).

### 5.2 Attribution

CC BY 4.0 requires naming the publisher, a license link, and an indication of changes. The citation is the one the Max Rubner-Institut asks for:

> Nährwertdaten: Max Rubner-Institut (2025): Bundeslebensmittelschlüssel (BLS), Version 4.0 - Deutsche Nährstoffdatenbank. Karlsruhe. DOI: 10.25826/Data20251217-134202-0. Lizenz: CC BY 4.0 (https://creativecommons.org/licenses/by/4.0/). Auszug; Werte vereinheitlicht, Bezeichnungen, Portionsgewichte und Dichten ergänzt. — Additional data: U.S. Department of Agriculture, FoodData Central (public domain).

Shown in `nutrition-data/ATTRIBUTION.md`, the repo README, `manifest.json` (served with every recipe nutrition sheet and the admin dataset endpoint), the app's nutrition sheet, and the admin catalogue screen.

### 5.3 Harmonisation

The target is the EU labelling definition used by BLS.

| Value | BLS | FDC |
|---|---|---|
| Protein | `PROT625` | Nitrogen 1002 × 6.25 where present; otherwise 1003, which uses food-specific factors (flagged) |
| Fat | `FAT` | 1004 |
| Saturated fat | `FASAT` | 1258 |
| Carbohydrates (available) | `CHO` | 1050 "by summation" where present; otherwise 1005 "by difference" minus fibre 1079, floored at 0 |
| Sugar | `SUGAR` | 2000, else 1063 |
| Fibre | `FIBT` | 1079 |
| Salt | `NACL` | Sodium 1093 mg × 2.5 / 1000 |
| Energy kcal | recomputed | recomputed |
| Energy kJ | recomputed | recomputed |

Energy is always recomputed from the harmonised macros with the EU factors, for both sources:
- kcal: 4 protein, 4 carbohydrate, 9 fat, 7 alcohol, 3 organic acids, 2.4 polyols, 2 fibre
- kJ: 17, 17, 37, 29, 13, 10, 8

A part never exceeds its whole: saturated fat is capped at fat, sugar at carbohydrates. The sources measure them separately, and at trace levels a part can come out above its whole (BLS plantain: 0.07 g fat, 0.116 g saturated fat), which a label must not show.

FDC has no organic acids or polyols; they count as 0 there. The build compares the recomputed BLS energy with BLS's own `ENERCC` and fails if more than 1 % of foods deviate by more than 3 %, which proves the formula is implemented the way BLS does it.

### 5.4 From sources to catalogue

All rules live in `nutrition-data/scripts/cookpal_nutrition/catalogue/` and are covered by tests.

**BLS code structure.** Verified on the 4.0 data (the manual only defines the first letter, see `SOURCES.md`).

| Code part | Meaning | Example |
|---|---|---|
| Position 1 | Main group | `G` vegetables, `X`/`Y` dishes |
| Positions 1–5 | Food and its processing | `K1101` potato peeled |
| Position 6 (numeric codes only) | Preparation: `0` unprepared, `3` boiled, `4` braised/steamed, `5` stewed, `6` baked, `7` grilled, `8` pan-fried, `9` deep-fried | `K110132` boiled |

**Inclusion.** BLS groups `X` and `Y` (dishes) are excluded; everything else is included. FDC
contributes Foundation Foods (category *Restaurant Foods* excluded), SR Legacy foods of the groups
in `srLegacyCategories`, and the single foods under `include`; all of them go through the duplicate
rule against BLS. SR Legacy also contributes portions. Foods whose protein, fat or carbohydrates are missing are left out. `inclusion.yaml`
holds per-food exceptions (e.g. broths from group X, restaurant items in other FDC categories).

**States.** Processing states come from state words in the source names (lexicons); preparation
comes from code position 6 for numeric BLS codes, otherwise from preparation words in the names.

**Variants.** A prepared food is a variant of, in order: the food `families.yaml` attaches it to;
its family's only unprepared food; the one unprepared food whose name equals its own without
preparation words and preparation context ("ohne Fett", "Pfanne"); the member of an all-prepared
family whose code says unprepared ("Polenta gekocht"). Otherwise it is a food of its own.
Unprepared foods are never variants of each other.

**FDC duplicates.** An FDC Foundation food duplicating a BLS food is left out
(`fdc_duplicates.py`): both English descriptions are reduced to naming words (qualifiers dropped,
plurals folded, American words read as British ones, implied words added); one must contain the
other, share the FDC lead word, and agree on energy; ties go to the most precise candidate in the
same state. Measured against a manual review of the 2026-04 release the rule agrees on 67 % and
errs towards "new"; the manual decisions where it disagrees are kept as corrections.

**Names.** A base food gets curated names, or names derived from its source descriptions: the
description without preparation words (and, for prepared foods, preparation context), split at
leading slash alternatives. A derived name two foods share belongs to neither. Fallbacks keep every
food named and every name unique: the full source description; for FDC foods (English only) the
English names in German too; the source code appended where a fallback is taken.

**Portions and density.** Every FDC food (Foundation and SR Legacy) is matched to its catalogue food
by the duplicate rule with a looser energy tolerance. Its household measures become portion weights
(count measures, size words scaled to a piece) or densities (volume measures), median per food;
densities outside 0.2–2.0 g/ml are ignored. A food with at most 5 kcal/100 g is negligible, and so is every spice (BLS group R2, FDC "Spices and Herbs").
`portions.yaml` and `properties.yaml` correct individual foods.

**Keys** are derived from source codes (`bls-K110100`, `fdc-321358`), stable across rebuilds.

### 5.5 Units

`units.yaml` defines language-neutral unit keys; `lexicon/<lang>.yaml` maps words to them (`EL`, `Esslöffel`, `EL gestr.` → `tablespoon`; `tbsp` → `tablespoon`). `IngredientUnitHelper` becomes a façade over the loaded lexicons, so the importers keep working.

| Kind | Examples | Conversion | Confidence |
|---|---|---|---|
| `MASS` | g, kg, mg | factor to g | exact |
| `VOLUME` | ml, l, tablespoon 15 ml, teaspoon 5 ml, cup-de 250 ml, cup-us 240 ml | factor to ml × food density; without density, 1.0 | exact / estimated |
| `COUNT` | piece, clove, bunch, can, slice, pack; size words small/large | food portion; size words ×0.7 / ×1.3 on `piece` unless a specific portion exists | per portion origin |
| `PINCH` | pinch, knife-tip, dash, drop | small fixed grams | estimated |
| `VAGUE` | some, to taste | no grams | — |

### 5.6 Producing the data (development workflow)

1. **Import.** Put new downloads into `raw/`, run `import_sources.py`.
2. **Review** the diff of `sources/` and of the dataset, and `local/build-report.txt`, which lists
   every rule decision worth a look (FDC duplicates, variants linked by name, dropped names,
   fallbacks, implausible densities, stale corrections).
3. **Correct** where a rule got it wrong: a line in the matching curation file, or a word in a
   lexicon or the FDC vocabulary when the fix is general. An LLM may propose corrections and
   names; nutrient values only ever come from the sources.
4. **Test** against production, locally (§12). Fixes are general; the food list is never extended
   one food at a time because users cooked something.
5. **Release** the dataset with the apiserver.

---

## 6. Shipping the dataset

### 6.1 Importer

`NutritionDatasetImporter` runs on `ApplicationReadyEvent` in a background thread, so startup and health checks are not delayed.

1. Read `nutrition/manifest.json` and recompute the checksum of the files it lists; on a mismatch the jar is broken and nothing is imported. If `nutrition_dataset_import` already has this checksum with status `DONE`, or `RUNNING` for less than 30 minutes, stop.
2. Insert the checksum row with status `RUNNING` in a transaction of its own. The primary key makes a concurrent second instance fail the insert and skip, which works on Postgres and H2 alike. A `FAILED` import, or a `RUNNING` one older than 30 minutes (the instance stopped), is taken over.
3. In one transaction, apply the dataset:
   - upsert `DATASET` foods by `catalogue_key`
   - replace their states, portions and `DATASET` names wholesale
   - keep `ADMIN` names, except one that now collides with a dataset name, which is dropped and noted in the report
   - mark dataset foods missing from the file `retired`
   - run the legacy merge (§4.3)
4. Mark `DONE` with a report (added, updated, retired, merged, dropped names). Rebuild the matcher index.
5. Existing ingredients are not relinked automatically; an administrator previews and applies relink runs (§8.1).

Until the first import finishes, the matcher reports "not ready": new ingredients stay unmatched (`link_source` null) until a relink run over "never matched or unlinked" is applied.

### 6.2 Read-only in practice

- Admin API and UI offer no edit for `DATASET` foods: no nutrients, states, portions or `DATASET` names.
- Admins can add `ADMIN` names to any food, and create, edit and delete `CUSTOM` foods (delete only when unreferenced; otherwise merge).
- A wrong dataset value is fixed in `curation/` and ships with the next release.

---

## 7. Matching

### 7.1 Where it runs

`CatalogueMatcher` is a Spring component in the apiserver.

**Index.** Lucene is a plain Java library (`lucene-core` + `lucene-analysis-common`, around 5 MB of jars), not a separate service. The index lives in the JVM heap (`ByteBuffersDirectory`) and is rebuilt from the database after import and after admin catalogue changes.

**Measured with Lucene 8.10** on real BLS names, 3–4-gram and stemmed fields:

| Names indexed | Index size | Heap | Query |
|---|---|---|---|
| 14,280 | 3 MB | 8 MB | ~1.3 ms |
| 142,800 | 29 MB | 33 MB | ~1.3 ms |

The full catalogue will have roughly 5,000–8,000 foods with names per family, so somewhere between the two rows, likely 10–25 MB of heap. The measurement is repeated on the real catalogue in phase 2. Lucene 10 (current, Java 21) is used in the implementation.

**Speed.** Matching a new ingredient while saving a recipe is one in-memory query, with no network call.

### 7.2 Language-neutral descriptors

The engine knows **concepts**, not words. Languages only contribute word lists, and anything unknown degrades confidence instead of breaking.

- **States** are a closed, language-neutral set in `states.yaml`: preparation states `RAW`, `BOILED`, `STEAMED`, `STEWED`, `BAKED`, `GRILLED`, `PAN_FRIED`, `DEEP_FRIED`, and processing states `DRIED`, `POWDERED`, `CONCENTRATED`, `CANNED`, `PICKLED`, `SMOKED`, `REDUCED_FAT`, `SWEETENED`. Only states that change nutrition per 100 g exist. "gehackt", "frisch", "TK" and "bio" are deliberately not states.
- **Catalogue foods get their states without language.** BLS preparation states come from code position 6 (§5.4), FDC states from its standard descriptors. Processing states (dried, canned) come from the family's code or descriptor and are checked in the sample review.
- **State words** are only needed to read what users type. They live per language in `lexicon/<lang>.yaml` (`getrocknet`, `getrocknete`, `dried`, `sun-dried` → `DRIED`) and are bootstrapped from the sources:
  - BLS names are an aligned German/English corpus with descriptors in fixed positions ("…, roh" / "…, raw", "…getrocknet" / "…dried")
  - FDC descriptions are comma-structured ("Tomatoes, sun-dried")
  - an LLM proposes the lists; review keeps them small
- **Stopwords** come from Lucene's built-in per-language stopword sets. Nothing is curated.
- **No list of neutral words.** A word that is neither a catalogue name, a state word, a unit nor a stopword is an *unexplained word*. It lowers confidence a little ("mehligkochende" in "mehligkochende Kartoffeln"). No language needs a list of harmless adjectives.
- **An unexplained part that is itself a catalogue name is a strong signal of a different food.** "Erdnuss" in "Erdnussbutter", "Kokos" in "Kokosmilch": the query names two foods, so a link to either lowers confidence sharply. This needs no grammar and works in any language.
- **Compound splitting** is dictionary-based against the catalogue vocabulary (`DictionaryCompoundWordTokenFilter`), applied to the *query*. It splits German, Dutch and Scandinavian compounds and is harmless for English and Romance languages.
- **Head position is never assumed.** German and English put the head last ("Frühkartoffel", "peanut butter"); Romance languages put it first ("pomodori secchi"). The scorer only asks which parts are explained and how, never where they sit.
- **Stemming** uses Lucene's light stemmer per enabled language. Each catalogue name is analysed with its own language; a query is analysed with all enabled languages and the best result per candidate counts. N-grams cover what stemming misses, independent of language.

**Adding a language** means: a lexicon file, names in `curation/`, and enabling it. No code changes.

### 7.3 Pipeline

1. **Normalise:** Unicode NFKC, lowercase, accent folding, remove bracket content, numbers and percentages; hyphens separate words ("Bio-Eier").
2. **Tokenise and explain** each word as a name word, `STATE(key)`, `UNIT` or `STOPWORD`. Stems come from Lucene's light and minimal stemmers per language; a typed word gets the stems of every enabled language.
   - **Compounds** are split against the catalogue's vocabulary only when covered completely, allowing one joining letter between parts and two letters of ending ("schwein-e-fleisch", "kartoffel-n"); two-letter food words ("Öl", "Ei") only at a word's edge. Catalogue words may also split off one unknown part next to a known one ("Speise-zwiebel"); typed words may not ("mehligkochend" never yields "Mehl").
   - A unit or state inside a typed compound needs no explaining and a state part adds its state ("Knoblauch-zehen", "Knoblauch-pulver") - unless the typed word is itself a catalogue word ("Glasnudeln").
3. **Apply rules.** A `NOT_A_FOOD` rule on the normalised name ends matching.
4. **Retrieve** the top 20 names across the exact, stemmed and n-gram fields.
5. **Resolve variants.** For each candidate food, pick the variant whose states equal the query's states. A variant name (Tomatenmark) selects itself. Without a state word, the base food applies. If the query has states but no variant matches, the base is kept with a strong penalty ("gebratene Kokosnuss" when no fried variant exists).
6. **Extract features** (`MatchFeatures`):
   - query coverage: how much of what the typed name names the candidate's name explains (words naming nothing known are counted separately)
   - candidate coverage: how much of the candidate's name the typed name explains
   - exact: every word of each name is a word of the other
   - fuzzy share: explanation resting on typos (edit distance 1, or 2 in words over 9 letters)
   - unexplained words ("mehligkochende")
   - conflicting food words: typed words or parts naming another food ("Erdnuss" in "Erdnussbutter")
   - other food words: candidate words or parts naming another food that the typed name does not explain
   - missing and extra states
   - other language: the matched name is in another language than the owner's ("Paprika")
   - margin to the runner-up
7. **Score:** confidence = logistic function over the features, with weights in `matcher-weights.json`. `CatalogueMatcherCalibrationTest` (`mvn test -Pcalibrate-matcher`) fits them on the gold sets - the local production set too where present - in two stages: the ranking features as a conditional logit (the right candidate of each name against its rivals), then scale, bias and margin weight on the first candidates only, so that bands mean what they say. It writes new weights only when they do at least as well as the shipped ones. Only the fitted weights are committed, a handful of numbers that contain no user text.
8. **Filter** out candidates hit by a `NEVER_LINK_TO` rule for this name.
9. **Bands:** starting values; calibration checks them against §12.

   | Confidence | Result |
   |---|---|
   | ≥ 0.90 | Link, silent |
   | 0.60–0.90 | Link, marked uncertain |
   | < 0.60 | No link |

Each result writes `link_confidence`, `link_matcher_version` and `linked_at`. The matcher version is a constant bumped with every change to logic or weights.

### 7.4 Creating ingredients

`IngredientService.createOrGetIngredient(name, user)`:
1. The user's own ingredient with that exact name exists → return it, including its link or exclusion.
2. Otherwise create it and match it (`AUTO`).

There is no public-ingredient path any more.

For recipe requests, a supplied ingredient id is honoured only if the ingredient belongs to the user. Otherwise the name is used. The same applies to ingredient-need ids.

### 7.5 Embeddings (phase 6, optional)

A small multilingual embedding model (e.g. `multilingual-e5-small`, int8 ONNX, around 120 MB, CPU) runs in the ML subsystem, which already ships onnxruntime. The apiserver sends catalogue names after each import. Embeddings contribute one more feature (cosine similarity of the best candidate) for names below the silent band, and only in relink runs and an async pass after ingredient creation, never while saving a recipe. The feature ships only if recalibration raises gold-set coverage without lowering precision; embeddings place "Milch" and "Kokosmilch" close together, so that has to be proven.

---

## 8. Admin: migration cockpit

### 8.1 Relink runs

- **Scope options:**
  - never matched or unlinked
  - `AUTO` links below a confidence
  - all `AUTO` links
  - links to retired or merged foods
  - links from an older matcher version

  `USER` and `ADMIN` links are never in scope.
- **Preview** is a dry run that deduplicates by normalised name first. Proposals are stored in:
  - `ingredient_relink_run`: scope, matcher version, dataset version, status, counts, started by, timestamps
  - `ingredient_relink_proposal`: run, normalised name, affected ingredient count, old food, old confidence, new food, new confidence, change type `NEW_LINK` / `CHANGED` / `UNLINKED` / `UNCHANGED`, decision `PENDING` / `ACCEPTED` / `REJECTED`
- **Review screen:**
  - stat tiles per change type and confidence band
  - a table grouped by name with affected count, old → new food with kcal/100 g for both, and confidence
  - filters
  - bulk decisions
  - sample of affected recipes
- **Reject** offers "only this run" or "remember". Remembering writes a `NEVER_LINK_TO` rule (or `NOT_A_FOOD` when the proposal is to unlink), so the next run does not propose it again.
- **Apply** writes accepted proposals in batches as `AUTO` with `link_run_id`. An ingredient changed after the preview (`last_change`) is skipped and counted.
- **Revert** restores old values for ingredients whose `link_run_id` is still this run.
- **History** lists every run. Runs are only ever started by an administrator.

### 8.2 Catalogue screen

Replaces the current Ingredients screen.
- **Food list:** origin, source code and name, states, variants, portions, nutrients, retired.
- **Dataset foods** are read-only apart from adding names.
- **Custom foods:** create, edit, delete when unreferenced.
- **Legacy foods** left over from the migration get their own filter.
- **Tiles:** dataset version and import report.
- **Removed:** the JSON import dialog (decision 4).

### 8.3 Unmatched names and export

- **List:** distinct normalised user ingredient names below the silent band, with user count and top three candidates.
- **Actions:**
  - add as name of a food (creates an `ADMIN` name, then offers a scoped preview)
  - mark as not a food (rule)
  - link as `ADMIN` for a single ingredient when the name is ambiguous across users
- **Export:** names with user counts as JSON into `nutrition-data/local/` (gitignored), to build and refresh the production gold set (§12). Used for testing only, never committed.

### 8.4 Merge

"Merge custom food A into food B" repoints every ingredient (keeping `link_source`), moves non-conflicting `ADMIN` names, then deletes A.

### 8.5 Coverage report

Runs the calculator over all recipes on the instance and shows:
- share of recipes `COMPLETE` / `INCOMPLETE` / `UNAVAILABLE`
- the most common warning causes
- the names causing most warnings

This is how the §12 ship gate is measured on production.

### 8.6 User corrections as suggestions (phase 5)

Aggregates `USER` links that differ from what the matcher, following the name rules, now suggests ("14 users linked *Kräuterquark* to *quark*"), and names users excluded where no rule says they are no food. One click adds the name to the food as `ADMIN`, or writes a `NOT_A_FOOD` rule; existing ingredients follow with a relink run. The curator carries recurring corrections into `curation/` for the next release.

---

## 9. Calculation

### 9.1 Per line

1. **Link:** `excluded_from_nutrition` → `EXCLUDED`; no food → `UNLINKED`.
2. **Grams:** unit resolved through the lexicons (§5.5). An unknown unit gives `UNIT_UNKNOWN`; a count unit without a portion gives `NO_PORTION`; a missing amount or a `VAGUE` unit gives `NO_AMOUNT`.
3. **Values:** grams × food per 100 g.
4. **Uncertainty flags:** link confidence below the silent band; volume without density; estimated portion; size-scaled portion; pinch.

### 9.2 Impact-based status

A line **warns** when:
- it is `UNIT_UNKNOWN` or `NO_PORTION` (unknown contribution), unless its food is `negligible`
- it is `UNLINKED` and could contribute ≥ 10 % of the recipe's energy even as rich as fat (9 kcal/g): its mass, its volume as water or its pinch, times 9; always where its amount is pieces or missing ("1 Prise Xanthan" does not warn, "200 g Gulasch" and "2 Sternanis" do)
- it is `NO_AMOUNT` on a food with ≥ 100 kcal/100 g and not `negligible` ("etwas Öl" warns, "Petersilie" and "Salz n. B." do not)
- neither `UNLINKED` nor `NO_AMOUNT` warn where the name says the ingredient is used only a little ("Mehl für die Form"; the lexicons' `sparingUses`)
- it is resolved but uncertain **and** contributes ≥ 10 % of the recipe's energy

Recipe status:

| Status | When | App shows |
|---|---|---|
| `COMPLETE` | no line warns | values |
| `INCOMPLETE` | a line warns | values, marked incomplete in the warning colour |
| `UNAVAILABLE` | at least half of the lines that are not silent warn | only "Nährwerte ergänzen" |

The thresholds are initial values and are tuned with the coverage report (§8.5).

### 9.3 API

- **`RecipeResponse.nutrition`** (null when the feature is off or the catalogue is not ready):
  - `basis`: `SERVING` or `RECIPE` when servings is 0
  - `status`
  - `values`: `energyKcal`, `energyKj`, `fat`, `saturatedFat`, `carbohydrates`, `sugar`, `fibre`, `protein`, `salt`
  - `warningCount`
- **`GET /api/v1/recipes/{id}/nutrition`** returns the details:
  - per line: need id, ingredient id and name, grams, values, linked food (id, display name in the requested language, source name and type), confidence, state, flags, whether it warns
  - the attribution text
- **`GET {SharePaths.PUBLIC_BASE}/{shareId}/nutrition`** returns the same for shared recipes, read-only, next to the existing shared recipe and image routes.
- **`GET /api/v1/catalogue/search?q=&lang=`** returns ranked foods with confidence and kcal/100 g, for the link dialog.
- **`PUT /api/v1/ingredients/{id}/link`** takes `{ "catalogueFoodId": 12 }` or `{ "excluded": true }` and sets `USER`, confidence 1, clears `link_run_id`. Owner only.
- **`GET /api/v1/ingredients`** returns the user's own ingredients with ids, plus catalogue display names in the request language without ids, for autocomplete. Released apps already treat id-less entries as "create by name".
- **`IngredientResponse`** loses the nutrient fields. The app never read them.

The calculator is a read-only service over loaded entities plus in-memory lexicons. Recipe lists use existing batch fetching; no caching until measured.

---

## 10. App

- **`RecipeDetailView`**, a full-width row between dividers right below the ingredients and their servings: "Nährwerte" with the energy per serving, or "gesamt" without servings. `INCOMPLETE` adds "unvollständig" and turns the icon and text to the error colour. `UNAVAILABLE` shows only the call to action. No row while the catalogue is not ready.
- **Nutrition sheet** (tap on the value):
  - all values for the basis, following the servings scaling for totals
  - line list with grams, kcal and state chip; warning lines first
  - what the estimate does not take into account, and the attribution, at the bottom
  - for lines counting pieces of unknown or guessed weight: "Gewicht pro Stück angeben" (phase 5)
  - loaded from the details endpoint when opened
- **Link dialog** (tap on a line):
  - search prefilled with the ingredient name
  - candidates with display name, source name and kcal/100 g
  - "nicht relevant für Nährwerte"
  - a note that the choice applies to all recipes using this ingredient
- **Shared recipes** show the sheet without link actions.
- **Strings** in de and en. Values always read "≈" and the sheet explains "Schätzung".

---

## 11. Security fix (phase 0)

`RecipeController` binds `IngredientNeed` and `Ingredient` entities from the request, and `RecipeService.createMissingIngredients` keeps any supplied ingredient id.

- **Ingredient ids:** replace entity binding with request DTOs. A supplied ingredient id is used only if it belongs to the caller; otherwise the name is used. Ingredient-need ids are honoured only for needs of the recipe being updated.
- **Tests:** an integration test per case (foreign ingredient id, foreign need id).

This is independent of nutrition and should ship first.

---

## 12. Evaluation and ship gate

There are two gold sets, because production data never enters git.

| Set | Source | Where | Runs |
|---|---|---|---|
| **Reference set** `gold/names-reference.tsv` | Written for the purpose, not from production: common ingredient lines in German and English with typos, plurals, state words, compounds, brand-free product names, and deliberate traps (Erdnussbutter, Kokosmilch, getrocknete Tomaten, "Salz und Pfeffer"). Drafted with an LLM, to be reviewed; ~600 lines so far. Written by the same hand as `curation/synonyms/`, so it flatters the matcher - the production set is the real measure | committed | CI, on every build |
| **Production set** `local/names-production.tsv` | The admin export (§8.3), labelled with expected foods with the help of an LLM and reviewed | `nutrition-data/local/`, gitignored | locally, via `mvn verify -Pproduction-gold`; the test is skipped when the file is absent |

PRs that change matcher, weights or dataset quote the production-set numbers (aggregates only) in their description.

**Java tests:**

| Test | Checks | Runs |
|---|---|---|
| `DatasetIntegrityTest` | manifest checksum, unique names, display names, plausibility bounds, energy recomputation | CI |
| `CatalogueMatcherReferenceSetTest` | precision and coverage on the reference set, as a regression floor | CI |
| `CatalogueMatcherProductionSetTest` | precision and frequency-weighted coverage on the production set | local, profile `production-gold` |
| `NutritionCalculatorGoldRecipesTest` | median absolute percentage error on `local/gold-recipes.tsv` | local; skips without the file |
| `CatalogueSplitMigrationIntegrationTest` | §4.2 | CI |

**Ship gate:** all must hold before the app shows values (phase 4 release).

| Metric | Measured on | Target |
|---|---|---|
| Precision of silent links | production set | ≥ 97 % |
| Frequency-weighted coverage (silent + uncertain) | production set | ≥ 90 % |
| Median absolute energy error per serving | gold recipes (40) | ≤ 20 % (measured: 11 %) |
| Recipes with warning (`INCOMPLETE` + `UNAVAILABLE`) | coverage report, production | < 30 % |
| Recipes `UNAVAILABLE` | coverage report, production | < 10 % |

The reference set's CI floor is set from its first measured result and only moves up.

`local/gold-recipes.tsv` (never committed: Chefkoch's data) holds ingredient lines and a reference energy per serving, taken from Chefkoch's `kCalories` by `scripts/fetch_gold_recipes.py` (54 everyday dishes searched, the most-voted recipe per dish that states calories; 40 dishes had one with usable servings and form the set; decided 2026-09-13). Only servings, the reference value and each line's amount, unit and name are stored - no recipe text, images or authors, and no production recipes. The references are estimates too, and some are for a whole cake rather than a piece, so the median is checked: a sanity bound.

Known limits, explained in the app: frying oil left in the pan, raw vs cooked weight, drained liquids, brand differences.

---

## 13. Out of scope

- **`additional_info`** is per recipe line information stored on the ingredient ("fein gehackt" from the first recipe wins for all later ones) and never shown by the app. Moving it to `ingredient_need` is a separate change. The catalogue split keeps the column untouched.
- **Weekly nutrition totals** in the week plan. A follow-up once recipe values are trusted.
- **Micronutrients.** BLS has them, but they are not in v1.

---

## 14. Phases

Each phase ends green and is releasable.

### Phase 0 — Groundwork
- Security fix (§11).
- `nutrition-data/` scaffold: `SOURCES.md`, `ATTRIBUTION.md`, gitignored `raw/`, `extract_bls.py`, `extract_fdc.py`, committed extracts. Delete `ingredientsources/`.
- Confirm the BLS code structure against the reference manual (§5.4); measure the FDC group sizes.
- Admin name export on the **current** schema (distinct normalised names, user counts, ≥ 5 users) into the gitignored `nutrition-data/local/`, for the production gold set only.
- `local/gold-recipes.tsv`. Both name sets are labelled in phase 1 once catalogue keys exist.

### Phase 1 — Catalogue split and dataset
- `V17__catalogue_split.sql` with guards; migration test; production-copy dry run with recorded counts.
- Entities `CatalogueFood`, `CatalogueFoodName`, `CatalogueFoodPortion`, `NutritionDatasetImport` (`CatalogueNameRule` comes with the rules of phase 3); `Ingredient` slimmed; `IngredientAlternativeNames` removed; `IngredientService` rewritten without `populateNutrients`.
- Dataset: inclusion rules and exceptions, FDC duplicates, families and variants, states, names, lexicons, units, harmonisation, `build_dataset.py`; `DatasetIntegrityTest`. Write and label `gold/names-reference.tsv`; label `local/names-production.tsv`. Maven profile `production-gold`.
- `NutritionDatasetImporter` (§6.1) including legacy merge; links stay as migrated until an administrator applies a relink run.
- `opencookbook.nutrition.enabled` (default `false`): when off, no import, matching, calculation, nutrition endpoints or admin nutrition screens; `nutritionEnabled` in the instance info. The V17 migration runs regardless, since it is a schema change. Enabling the flag later imports the dataset on the next start; relinking existing ingredients stays manual.
- API: `GET /ingredients` as in §9.3; `IngredientResponse` without nutrients; admin catalogue endpoints replace admin ingredient endpoints.
- Admin catalogue screen (§8.2) replacing the Ingredients screen. JSON dialog removed.
- `IngredientUnitHelper` backed by lexicons.

### Phase 2 — Matcher and calculator (backend only)
- `CatalogueMatcher` (§7), `CatalogueMatcherCalibrationTest`, `matcher-weights.json`. (`IngredientMatcher` is already gone with phase 1; the fuzzy-matcher dependency stays, recipe search uses it.)
- `createOrGetIngredient` as in §7.4.
- `NutritionCalculator`, the `nutrition` summary, and the details, search and link endpoints. The app does not use them yet.
- Gold-set tests enforced.

### Phase 3 — Migration cockpit
- Relink runs with preview, decisions, rules, apply, revert and history (§8.1). Relinking existing ingredients is always started by an administrator.
- Unmatched names, merge, coverage report (§8.3–8.5).
- First manual relink on production. Check the ship gate (§12); iterate on curation or matcher until it holds.

### Phase 4 — Nutrition in the app
- Recipe value, nutrition sheet, link dialog, shared view, attribution, i18n (§10).
- Release once the ship gate holds.

### Phase 5 — Feedback loop
- User corrections as suggestions (§8.6).
- Per-user portion override ("1 Stück wiegt bei mir 200 g"): `ingredient_portion_override (ingredient_id, unit_key, grams)`, offered on lines whose piece weight is unknown, estimated, size-scaled or a typical container size. The owner's weight counts before the catalogue's; a small or large piece is a share of the owner's ordinary one.
- Second dataset round.

### Phase 6 — Embeddings (optional, gated; deferred)
- §7.5. Ships only if recalibration improves the production set without lowering the reference set. Left for the future: matching works well enough without.

---

## 15. Open points

None. All decisions are recorded in §2.
