# nutrition-data

The food catalogue behind cookpal's nutrition estimation, and the tooling that builds it. Nothing
in this directory is packaged: the apiserver ships only the generated dataset in
`src/main/resources/nutrition/` and imports it into its database when it starts.

This document is meant to be enough to import a new release of the sources, review what the build
decided and write the curation that corrects it - without reading the code first.

- [What the dataset is](#what-the-dataset-is)
- [Layout](#layout)
- [Setup](#setup)
- [Importing a new BLS or FDC release](#importing-a-new-bls-or-fdc-release)
- [How the build decides](#how-the-build-decides)
- [Curation files](#curation-files)
- [The build report, topic by topic](#the-build-report-topic-by-topic)
- [Checking the result in the apiserver](#checking-the-result-in-the-apiserver)
- [Gold sets](#gold-sets)
- [Rules for contributors](#rules-for-contributors)

## What the dataset is

A **catalogue** of foods with nutrients per 100 g, drawn from:

- **BLS 4.0** (Bundeslebensmittelschlüssel, Max Rubner-Institut): every food except dishes. German
  and English descriptions, preparations coded in the food code.
- **USDA FoodData Central**: Foundation Foods that duplicate no BLS food; SR Legacy foods only from
  food groups BLS and Foundation Foods lack (herbs and spices) and single foods listed by id. SR
  Legacy and Foundation household measures also give portion weights and densities.

Every catalogue food has:

| Field | Meaning |
|---|---|
| `key` | Stable across releases: `bls-<code>` or `fdc-<fdc id>`. Curation refers to foods by this key or by the bare source code. |
| `variantOf` | For a prepared food, the key of the unprepared **base** it was prepared from (`bls-K110132` boiled potato → `bls-K110100` potato). Bases have no `variantOf`. |
| `states` | Language-neutral states that change nutrients per 100 g: preparations (`BOILED`, `PAN_FRIED`, …) and processing (`DRIED`, `CANNED`, `SMOKED`, …). See `curation/states.yaml`. |
| `names` | Per language, the first is the display name. **Only bases have names**; a variant is found through its base plus the state words someone types ("gekochte Kartoffeln"). Names are unique per language across the whole catalogue, ignoring case. |
| `nutrients` | Per 100 g, harmonised to the EU labelling definitions (see below). |
| `portions` | Weights of count units (`piece`, `slice`, `can`, …) for this food. |
| `densityGPerMl` | For turning volumes into grams. |
| `negligible` | Contributes next to nothing (≤ 5 kcal/100 g): water, salt. A recipe line of it never warns. |

Besides the catalogue the dataset ships `units.json`, `states.json` and `lexicons.json` (words for
units and states per language) and `manifest.json` with the label, a checksum over all files, the
source releases used and their attribution texts.

**Harmonisation.** BLS already follows Regulation (EU) 1169/2011. FDC is brought to it: available
carbohydrates (by summation, else by difference minus fibre), protein as nitrogen × 6.25, salt from
sodium × 2.5. Energy is recomputed for every food from the macronutrients with the EU factors, the
formula BLS uses, so values from both sources agree. Saturated fat is capped at fat and sugar at
carbohydrates. Nutrient values are only ever taken from the sources.

## Layout

| Path | Content | In git | Made by |
|---|---|---|---|
| `raw/` | Source downloads, see `SOURCES.md` | no | you, by downloading |
| `sources/` | Extracts of the downloads: only the fields cookpal uses, values unchanged; `provenance.json` records which download each came from | yes | `import_sources.py` - never edit by hand |
| `curation/` | Reference data and corrections to the build's rules | yes | people (and LLMs) |
| `gold/` | Test sets written for the purpose, never from production | yes | people |
| `local/` | `build-report.txt` of the last build; production exports, the production gold set and the gold recipes | **never** | the build; you; `gold-recipes.tsv` by `fetch_gold_recipes.py` |
| `scripts/` | Python tooling (`cookpal_nutrition/` holds the rules) and its tests | yes | |
| `../src/main/resources/nutrition/` | The shipped dataset | yes | `build_dataset.py` - never edit by hand |

`sources/` is committed so that a new release shows up as a reviewable diff of plain CSV.

## Setup

Python is pinned in `.python-version` and managed with pyenv.

```sh
cd nutrition-data
pyenv install --skip-existing "$(cat .python-version)"
"$(pyenv root)/versions/$(cat .python-version)/bin/python" -m venv .venv
.venv/bin/pip install -r requirements.txt
.venv/bin/python -m pytest
```

## Importing a new BLS or FDC release

1. **Download** the release into `raw/` under the publisher's file name (`SOURCES.md` lists names
   and addresses). Older downloads may stay; the newest file of each source is used.
2. **Import:**
   ```sh
   cd nutrition-data/scripts
   ../.venv/bin/python import_sources.py
   ```
   It extracts `sources/` from the downloads, records them in `sources/provenance.json`, builds the
   dataset into `src/main/resources/nutrition/` and writes `local/build-report.txt`.
   (`build_dataset.py` does only the build, for when only curation changed.)
3. **Read the diffs** of `sources/` (what the publisher changed) and of the dataset.
4. **Review the report** (`local/build-report.txt`) topic by topic, as described
   [below](#the-build-report-topic-by-topic), and correct what is wrong in `curation/`. Rebuild with
   `build_dataset.py` after each change. Start with *stale curation*: it lists corrections that no
   longer apply because the release renamed or removed a food.
5. **Bump the label** in `curation/dataset.yaml` (`2026.1` → `2026.2`). The label is the
   human-readable version of *this* dataset, shown to administrators; the manifest's checksum
   identifies the exact content. Change it whenever sources or curation change.
6. **Check** in the apiserver, see [below](#checking-the-result-in-the-apiserver).
7. **Update attribution** if a source's release or license text changed: `ATTRIBUTION.md`, and the
   texts in `scripts/cookpal_nutrition/dataset.py` that end up in the manifest.

A new release needs no curation to build: every decision is taken by a rule that works on the
structure of the sources. Curation only corrects individual decisions, and stale corrections are
reported and ignored rather than breaking the build. The build stops only where shipping would be
wrong: when the energy recomputation no longer reproduces BLS (the source changed its formula;
`scripts/cookpal_nutrition/nutrients.py` must follow), or when curation contradicts itself (one name
given to two foods).

When an apiserver with the new dataset starts, it imports it once (per checksum): dataset foods are
updated by key, foods the release no longer has are *retired* (kept, as ingredients may link to
them), and names administrators added are kept unless the release gives them to another food.

## How the build decides

The build runs these steps (`scripts/cookpal_nutrition/catalogue/`):

1. **Inclusion.** BLS groups in `inclusion.yaml` `excludedGroups` (`X`, `Y`: dishes) are left out;
   `include`/`exclude` override single codes. FDC Foundation Foods minus `excludedCategories`, SR
   Legacy foods of `srLegacyCategories`, plus single FDC ids under `include`, minus `exclude`. Foods
   lacking protein, fat or carbohydrates are left out.
2. **States.** Processing states from state words in the descriptions (`lexicon/<lang>.yaml`).
   Preparation from BLS code position 6 for purely numeric codes (`0` unprepared, `3` boiled, `4`
   steamed/braised, `5` stewed, `6` baked, `7` grilled, `8` pan-fried, `9` deep-fried), otherwise
   from preparation words. See `SOURCES.md` for what the code means.
3. **Families and variants.** BLS codes sharing their first five characters form a family. A
   prepared food becomes a variant of: the food `families.yaml` `attach` names; else its family's
   only unprepared food; else the unprepared food whose name equals its own without preparation
   words; else it stays a base of its own. Foods under `families.yaml` `bases` are bases even though
   prepared (bought that way), and their family's other preparations become their variants.
4. **FDC duplicates.** An FDC food duplicates a BLS food when their English descriptions share the
   naming words (after `fdc-vocabulary.yaml` maps American to British words) and their energy agrees
   within a tolerance. Duplicates are left out; `fdc-duplicates.yaml` corrects single decisions.
5. **Names**, for bases only:
   - Curated names in `names/*.yaml` replace a food's derived names.
   - Otherwise names are derived from the descriptions: preparation words taken out; a leading
     slash list gives one name per alternative ("Dorsch/Kabeljau"); a bracketed word sharing the
     first or last four letters of a word of the name is a synonym ("Lachs geräuchert
     (Räucherlachs)").
   - A derived name two foods share belongs to neither; a food left without a name gets its full
     description, with the source code appended if even that is taken. An FDC food without a German
     description gets its English names in German too.
   - `synonyms/*.yaml` adds everyday names after whatever names a food has; a derived name another
     food has as a curated name or synonym is dropped.
6. **Portions and densities** from FDC household measures of the food or its FDC duplicate, then
   `portions.yaml` and `properties.yaml` on top. **Negligible** when energy ≤ 5 kcal/100 g, unless
   `properties.yaml` says otherwise.
7. **Diet class** (`VEGAN`, `VEGETARIAN`, `MEAT`) from the BLS group letter in `diet-classes.yaml`
   `groupDefaults`; variants take their base's. A name matching a `reviewPatterns` entry stricter
   than its group is **nominated**, never classified, and an unanswered nomination **fails the
   build**: patterns misfire too often to decide ("Wildreis", "Collards", "Erdnussbutter",
   "Honigmelone", "alkoholfrei", "Vegetarische Bratwurst"). `overrides` is the only thing that
   settles a food; groups without a default (`Q`) and all FDC foods are listed there in full.

## Curation files

All curation is keyed by source codes or catalogue keys, so it keeps working with later releases as
long as the publisher keeps the codes; entries for codes a release no longer has are reported as
stale and ignored. Curation files are written by people - nothing generates or updates them. Each
file is short because it only lists **exceptions to rules**; the rules decide the other thousands
of foods.

### Reference data (independent of releases)

| File | Content | Format |
|---|---|---|
| `units.yaml` | Language-neutral units and how to turn them into grams | `key: {kind: MASS, grams: 1}`; `VOLUME` with `millilitres`; `COUNT` (weight from the food's portion; `sizeOf` + `factor` for a scaled variant: `piece-small: {kind: COUNT, sizeOf: piece, factor: 0.7}`; containers whose size hardly varies may state `typicalGrams` or `typicalMillilitres`, used for foods without a portion of their own: `can: {kind: COUNT, typicalGrams: 400}`); `PINCH` with fixed `grams`; `VAGUE` |
| `states.yaml` | States and their kind | `BOILED: {kind: PREPARATION}`; `unprepared: true` for `RAW` |
| `lexicon/<lang>.yaml` | Words per language for every unit and state, `preparationContext` phrases ("ohne Fett") that describe a preparation without being one, and `descriptions`: words saying how a food is cut, served or used ("gehackt", "lauwarm", "Form") | `units: {tablespoon: [EL, Esslöffel]}`, `states: {DRIED: [getrocknet]}`, `descriptions: [gehackt]`. Unit words cover every unit string the app and the recipe importers produce. State and description words need base forms only; the matcher stems them. A description a food's name lacks is not held against it; one it shares still counts for it. List only words that never tell two catalogue foods apart - not "frisch" or "gemahlen". |
| `fdc-vocabulary.yaml` | American → British words (`eggplant: aubergine`), implied words (`egg: chicken`), phrases that name nothing (`year round average`) | used by the FDC duplicate rule |

A new language needs a lexicon file, names for it in `names/` and `synonyms/` where derived names
are missing, and enabling it in the build (`LANGUAGES` in `scripts/cookpal_nutrition/catalogue/model.py`).

### Corrections to single decisions

| File | When to add an entry | Format |
|---|---|---|
| `inclusion.yaml` | A dish recipes use as an ingredient (`include`), or a ready-made product no recipe lists (`exclude`) | codes / FDC ids with a comment naming the food |
| `families.yaml` | *prepared food without an unprepared base* or a wrong *variant linked by name* | `attach: {T420132: T422100}` (prepared code → base code); `bases: [G860192]` |
| `fdc-duplicates.yaml` | *FDC food decided by rule* got it wrong | `<fdc id>: <BLS code>` or `<fdc id>: new`, with a comment: `# <description> (rule: <what the rule said>)` |
| `names/bls.yaml`, `names/fdc.yaml` | A food's derived names are poor, misspelled, or missing (every new FDC food needs German names) | `bls-K110100: {de: [Kartoffel geschält], en: [Potato peeled]}` - replaces all derived names; first per language is shown. Quote names containing commas. |
| `synonyms/*.yaml` | People write a food differently than its name ("Butter" for "Süßrahmbutter", "Möhre" for "Karotte") | same format; added, never shown first. List a form only if it is irregular (`Eier`) or another word - plurals, inflections and typos are the matcher's job. A synonym belongs to the food most recipes mean by it. |
| `portions.yaml` | A food recipes count in pieces, slices, packs or cubes has no weight for that unit; a container holds something else than its typical size (a drained can); an FDC weight follows US packaging where German recipes assume another | `bls-G480100: {piece: {grams: 150}}` (estimated) or `{piece: {grams: 110, fdc: 123456}}` (taken from an FDC measure) |
| `properties.yaml` | A food measured by spoon or cup has no density (a volume is then weighed as water), or density or negligibility is wrong | `bls-C133000: {density: 0.37}`, `bls-R111000: {negligible: true}` |
| `diet-classes.yaml` | The build stopped on a food nobody has classified, or a class is wrong | `overrides: {bls-R468000: MEAT}` with a comment naming the food. Answer **every** food the build listed; it will not ship a guess |
| `dataset.yaml` | Every release of this dataset | `label: "2026.2"` |

To find keys, search the built catalogue:

```sh
cd nutrition-data/scripts
../.venv/bin/python find_foods.py kartoffel geschält
../.venv/bin/python find_foods.py --file terms.txt      # one search per line
```

## The build report, topic by topic

`local/build-report.txt` groups the build's decisions by topic. For each topic: what to check, and
where to correct it.

| Topic | What to check | Corrected in |
|---|---|---|
| `summary` | Counts of foods, new FDC foods, portions and densities. Large jumps against the previous release deserve a look at the `sources/` diff. | - |
| `stale curation in <file> (ignored)` | A correction refers to a code the release no longer has. Find the food under its new code (`find_foods.py`) and move the entry, or delete it. | the file named |
| `FDC food decided by rule` | Every FDC food with "duplicates X" or "new". Check that "duplicates" names the same food (not parsley root for parsley leaf) and that "new" really has no BLS counterpart. | `fdc-duplicates.yaml`; recurring word differences in `fdc-vocabulary.yaml` |
| `variant linked by name` | The prepared food really is a preparation of that base. | `families.yaml` `attach` |
| `prepared food without an unprepared base (kept as a food of its own)` | Bought prepared (fine as is), or its base exists under another code (`attach`). | `families.yaml` |
| `preparation in code but not in name` | Informational: the code says prepared, the name does not. Wrong only if the food is clearly unprepared. | `families.yaml` `bases` |
| `derived name dropped` | Two foods derived the same name, or a curated name took it. The everyday word should belong to one of them. | `synonyms/` (give it to the right food) or `names/` |
| `full source description used as name` | The food had no usable name left. | `names/` |
| `no de name, other language used` | A new FDC food without German names. **Every one should get German names.** | `names/fdc.yaml` |
| `synonyms of a variant ignored` | Names belong to bases; the food became a variant. | move the synonyms to its base |
| `excluded, incomplete nutrients` | Informational. Include only if a later release fills the values. | - |
| `implausible density from FDC portions (ignored)` | Densities outside 0.2 to 2.0 g/ml; leafy herbs are legitimately light. | `properties.yaml` if a real density is known |
| `food without a diet class (no group default, no override)` | **Stops the build.** A food of a group with no default (`Q`), or an FDC food. Classify each in `overrides`. | `diet-classes.yaml` |
| `diet class nominated for review (name disagrees with group)` | **Stops the build.** The name reads stricter than the group. Decide whether the name or the group is right (both happen) and write the answer down, even when it only confirms the group. | `diet-classes.yaml` |
| `diet class overridden against its group` | Informational: every food curation classifies away from its group. A jump in these after a release means the group defaults no longer fit. | `diet-classes.yaml` |
| `names alike to the matcher, differing in energy` | Foods whose names differ only in numbers or bracketed words ("Gouda 30 % Fett", "Gouda 48 % Fett"). The matcher tells them apart when a typed name says the detail; a name that does not ("Gouda") gets one of them arbitrarily. Where recipes commonly write the plain name, give it to the food most recipes mean. | `synonyms/` |

After the build report, check the names that matter most in practice: search the everyday
ingredients recipes use (`find_foods.py`) and make sure each is reachable by its everyday word in
both languages, adding `synonyms/` where not. The matcher tests below measure exactly that.

## Checking the result in the apiserver

From the repository root (Java 21, see the main README):

```sh
# the dataset itself: checksum, unique names, display names, plausible nutrients, known units and states
mvn test -DskipAdminUi -Dtest=ShippedDatasetIntegrityTest
# matching and the whole estimate, on the gold sets
mvn test -DskipAdminUi -Dtest='CatalogueMatcher*Test,NutritionCalculatorGoldRecipesTest'
# everything
mvn test -DskipAdminUi
```

- `CatalogueMatcherReferenceSetTest` fails when silent-link precision or coverage on
  `gold/names-reference.tsv` drops below its floor. Its output lists every miss: a missing synonym,
  a wrong synonym, or a matcher weakness.
- `NutritionCalculatorGoldRecipesTest` fails when the median error per serving on `local/gold-recipes.tsv`
  rises above its ceiling. Recipes missing by more than half list the lines that contributed nothing
  - usually a missing portion weight or an unlinked ingredient. A recipe far above its reference
  often means a synonym on the wrong food (dry beans where recipes mean canned ones).
- When names changed a lot, refit the matcher's weights:
  `mvn test -DskipAdminUi -Pcalibrate-matcher` rewrites `src/main/resources/nutrition/matcher-weights.json`
  only if the new weights do at least as well on the gold sets. After changing matching logic or
  weights, bump `CatalogueMatcher.VERSION`.
- With a production gold set in `local/`, `mvn verify -DskipAdminUi -Pproduction-gold` also checks the
  release criteria on it: at least 97 % of silent links right, at least 90 % of names linked.

Floors and ceilings in these tests are the last measured results; they may only move towards better.

## Gold sets

| File | Content | Format |
|---|---|---|
| `gold/names-reference.tsv` | Ingredient names as recipes write them - plurals, adjectives, state words, compounds, typos, English - with the catalogue foods they mean, and traps where nothing fits ("Salz und Pfeffer") | `name<TAB>language<TAB>keys` with alternatives separated by `\|`, or `-` when no food fits |
| `local/gold-recipes.tsv` | 40 everyday recipes with Chefkoch's energy per serving, written by `scripts/fetch_gold_recipes.py` (only servings, the reference value and each line's amount, unit and name). **Never committed:** Chefkoch's data; the test skips itself without it. | `recipe<TAB>id<TAB>servings<TAB>kcal` and `line<TAB>recipe id<TAB>amount<TAB>unit<TAB>name` rows |
| `local/names-production.tsv` | Names users of an instance wrote, with how often, labelled like the reference set. **Never committed.** | as the reference set, plus a fourth column: occurrences |

**Writing the production set.** In the admin panel, *Unmatched names* → *Export names* downloads
`names-production.tsv` with every name used in a recipe of that instance, most frequent first. Its
third column holds the foods owners or administrators linked the name to (check them, people err
too), `-` where they excluded it, and `?` where nobody decided. Replace every `?` with the right keys
(`find_foods.py`) or `-`; the tests refuse a set with a `?` left. Save it as `local/names-production.tsv`.

When a release renames foods, the reference set's keys may go stale;
`CatalogueMatcherReferenceSetTest` names every unknown key. Relabel them with `find_foods.py`.

The reference set is written by the same hands as `synonyms/`, so it tends to flatter the matcher;
the production set is the honest measure.

## After a release reaches an instance

The apiserver imports the release on its next start and rebuilds the matcher's index. From then on
new ingredients are linked with the new catalogue. Existing ingredients keep their links until an
administrator relinks them, in the admin panel:

- **Relinking**: preview a run over a scope - *Matched by an older matcher* after `CatalogueMatcher.VERSION`
  was bumped, *Matched to retired foods* after a release dropped foods, *Matched below a confidence*
  or *Never matched or unlinked* to work through the uncertain rest. Accept or reject proposals (a
  rejection "remembered" becomes a name rule), apply, and revert a run that went wrong. Links owners
  or administrators made are never in scope.
- **Unmatched names**: the names not linked silently, with the matcher's three most likely foods.
  Add a name to a food (then preview a run to link the existing ingredients), mark a name as no food, or
  link a single user's ingredient where a name means different things to different users. The
  coverage report runs the calculator over every recipe and names the most common warnings.
- **User corrections**: names users linked to another food than the matcher suggests, or excluded,
  the most common first. Adopt one as a name of that food or as a name that is no food.
- **Name rules**: the remembered decisions, which automatic linking and every run follow.

What an instance teaches this way stays in its database. Carry recurring lessons - names added,
foods rejected for a name - into `curation/` for the next release.

## Rules for contributors

- **Never write nutrient values.** They come from the sources only. Curation may add names,
  synonyms, translations, portion weights (marked estimated), densities and corrections to rule
  decisions.
- **Never edit `sources/` or the shipped dataset by hand**; change curation or rules and rebuild.
- **Production data stays in `local/`.** It tests matching; it never decides which foods exist and
  never enters git.
- **Prefer a rule to a list.** When the report shows the same kind of mistake many times, change the
  rule in `scripts/cookpal_nutrition/` (with a test in `scripts/tests/`) rather than adding dozens of
  corrections. Rules must work on the structure of the sources, not on today's release.
- **Keep comments in curation files**: each entry says which food it is and why it is there, so the
  next release's reviewer can judge whether it still applies.
- Run `../.venv/bin/python -m pytest` after changing rules, and the apiserver checks above after
  every rebuild.
