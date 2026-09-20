# Database: recipe classification, diets and weekplan generation

What the migrations `V21`–`V25` added, and why each column is there. This is not a full schema
reference — it covers the tables and columns behind recipe suggestion, recipe diets, the
catalogue's diet classes and generated weekplans. For the nutrition catalogue itself see [`nutrition.md`](nutrition.md).

Schema changes are Flyway migrations in `src/main/resources/db/migration/`, applied at startup and
**immutable once released**. Bulk data is never shipped as a migration: the nutrition dataset,
diet classes included, ships as files in the jar and is imported afterwards (§6 of the nutrition
plan), so a dataset correction never needs a schema change.

---

## Two rules the schema exists to enforce

Almost every column below serves one of two rules. They are worth reading first, because they
explain why so many values are recorded in pairs.

1. **A decision a person made is never overwritten by one a machine made.** So wherever a value can
   be derived, the schema records *who decided it*. A run only ever touches values it can show a run
   derived; everything else is a person's.
2. **What cannot be read is recorded, not guessed.** A run that cannot classify a recipe writes down
   why, rather than picking the likeliest answer. The recipe keeps no value at all, which the
   application treats as "unknown" rather than as "plant-based" or "suits no meal".

A third rule follows from the first two: **every automatic change is reversible**, so a run records
what each recipe had before it touched it.

---

## Recipe: what a recipe is

### Diet (`recipe.recipe_type`)

| Column | Type | Meaning |
|---|---|---|
| `recipe_type` | smallint | `VEGAN`, `VEGETARIAN` or `MEAT`, stored as the enum's **ordinal** (0, 1, 2 — it predates the rest of this work). Null means unknown, which is not the same as vegan |

The ordering is load-bearing: the scale runs from least to most restrictive, and a recipe's diet is
the **strictest** of its ingredients'. Because it is stored as an ordinal, a new level may only ever
be appended, never inserted — and `V3` pins the range with `CHECK (recipe_type >= 0 AND
recipe_type <= 2)`, so adding one means widening that constraint, re-deriving every classified
recipe, and rebuilding the shipped dataset. **Fish counts as `MEAT`**; there is no pescatarian level.

The recipe holds only the value. Who decided it lives in `derived_classification` (below), so the
recipe table does not grow three bookkeeping columns for every attribute that can be classified.

### Dish role (`recipe.dish_role`)

| Column | Type | Meaning |
|---|---|---|
| `dish_role` | varchar(16) | What a recipe is when it is not a dish of its own: `SIDE` (served alongside one: rice, a side salad) or `COMPONENT` (goes into or onto another dish: a sauce, a dip, a dough, a stock). Null for a dish |

Sides and components are never planned or suggested. **Null counts as a dish**, so no recipe
disappears for missing metadata; a dish says which meals it suits in `recipe_meal_type`. Set by the
recipe's owner, in the app as one question with the meal types.

### Meal types (`recipe_meal_type`)

Which meals a recipe suits, as a set — most dishes are honestly both lunch and dinner.

| Column | Type | Meaning |
|---|---|---|
| `recipe_id` | bigint → `recipe` | Cascades on delete |
| `meal_type` | varchar(16) | `BREAKFAST`, `LUNCH`, `DINNER`, `SNACK` or `DESSERT`. Primary key together with `recipe_id` |

**No rows for a recipe means unknown, and unknown stays eligible** for every meal filter except
breakfast, where offering an untagged dinner is the most visible kind of wrong. A filter here only
ever narrows; it never hides a recipe for missing metadata.

Meal types are **only ever set by the recipe's owner**. Deriving them was tried and set aside:
reading a meal off a title produced too many confident wrong answers, and a wrong meal type is worse
than none because it takes a recipe *out of* the meals it really suits. Should it come back, it is a
new classification kind (below), not new columns.

---

## Catalogue food: whether a food is animal

| Column | Type | Meaning |
|---|---|---|
| `catalogue_food.diet_class` | varchar(16) | `VEGAN`, `VEGETARIAN` or `MEAT` — the same three-level scale a recipe carries, deliberately, so a recipe's diet is simply the strictest of its foods'. **Fish counts as `MEAT`** |
| `catalogue_food.diet_class_origin` | varchar(16) | `DATASET` where the shipped dataset said so, `ADMIN` where an operator corrected it |

A `CHECK` keeps the two columns in step: a class without an origin would be a decision nobody can
attribute, and an origin without a class would claim somebody decided nothing.

The origin is what makes a correction durable. A dataset import overwrites `DATASET` classes and
leaves `ADMIN` ones alone, so an operator's correction survives every later release. Unlike names
and nutrients, the diet class **may be corrected on a dataset food**: the shipped value is a reading
of a text description, not a measurement, so an operator who knows the food outranks it.

Where the shipped values come from — BLS food-group letters, with a curated exception list — is
described in `nutrition-data/README.md` under *How the build decides*.

---

## Classification runs: changing many recipes under review

A run over everybody's recipes is not something to do blind, so it works the way ingredient
relinking already does: **preview → decide → apply → revert**. Nothing changes a recipe until a
person applies the run.

Relink runs and classification runs share that lifecycle in code (`ReviewedRun`): the status, the
counts `proposal_count` / `applied_count` / `skipped_count`, `started_by_user_id`, `applied_at` and
`reverted_at` mean the same in `ingredient_relink_run` and `recipe_classification_run`. A step out of
order (applying a run that is not previewed, reverting one that is not applied, applying with
nothing accepted) is refused with `409 CONFLICT` for both.

The run machinery does not know what it classifies. Each *kind* (`DIET` today) supplies where its
value lives on a recipe and how to read it; runs, proposals and provenance store values as the kind
encodes them. Adding a kind adds no columns — only a value to the `kind` checks below.

### `recipe_classification_run`

| Column | Type | Meaning |
|---|---|---|
| `id` | bigint | From `recipe_classification_run_seq` |
| `kind` | varchar(16) | What the run classifies. `DIET` is the only kind today |
| `scope` | varchar(32) | Which recipes it looked at: `NEVER_CLASSIFIED` (the normal backfill: no value yet) or `DERIVED_ONLY` (re-read what earlier runs derived, e.g. after a dataset release). A person's value is in neither |
| `status` | varchar(16) | `PREVIEWED` → `APPLIED` → `REVERTED`, or `DISCARDED` |
| `basis` | varchar(64) | What the values were read from — for diets, the dataset release — so a later run explains a different outcome |
| `proposal_count` | integer | Recipes the run could decide about |
| `unreadable_count` | integer | Recipes it looked at and could not read |
| `skipped_count` | integer | Accepted, but changed since the preview and so left alone by apply |
| `applied_count` | integer | Recipes actually changed |
| `started_by_user_id` | bigint → `cookpal_user` | Null if the account is deleted later |
| `applied_at`, `reverted_at` | timestamptz | `applied_at` is also when every value the run derived was derived |
| `created_on`, `last_change` | timestamptz | From `AuditableEntity` |

### `recipe_classification_proposal`

One row per recipe the run looked at.

| Column | Type | Meaning |
|---|---|---|
| `id` | bigint | From `recipe_classification_proposal_seq` |
| `run_id` | bigint → `recipe_classification_run` | Cascades on delete; indexed |
| `recipe_id` | bigint → `recipe` | Cascades on delete |
| `proposed_value` | varchar(64) | What the run would make of it, encoded by the kind (`MEAT`); null when it could not be read |
| `previous_value` | varchar(64) | What the recipe had, so a revert restores exactly that |
| `previous_run_id` | bigint → `recipe_classification_run` | The run that derived the previous value; null where a person chose it or there was none. A revert hands the value back to that run rather than to nobody |
| `decision` | varchar(16) | `PENDING`, `ACCEPTED`, `REJECTED`, or `SKIPPED` — the run's own decision, meaning it looked and deliberately left the recipe alone |
| `reason` | varchar(512) | **Why**, in words a reviewer can check: `MEAT because of Hackfleisch`, or `not linked to the catalogue: Suppengrün` |

`reason` is not decoration. A bare "this recipe is MEAT" cannot be reviewed, and the column that
names the blocking ingredient turns the run into a work queue: link that ingredient and the recipe
becomes classifiable on the next run.

Applying re-checks each recipe and skips any whose value, or whose deriving run, changed since the
preview, so a run can never step on an owner who edited their recipe while the run sat waiting for
review.

### `derived_classification`: which values a run derived

| Column | Type | Meaning |
|---|---|---|
| `id` | bigint | From `derived_classification_seq` |
| `recipe_id` | bigint → `recipe` | Cascades on delete. Unique together with `kind` |
| `kind` | varchar(16) | Which of the recipe's values is meant |
| `run_id` | bigint → `recipe_classification_run` | The run that derived it; indexed. A revert restores only recipes still marked with that run, so it cannot undo a later run's work |

**A value without a row is a person's.** That covers every diet set before runs existed — no
backfill needed — and any code path that knows nothing about classification: forgetting to record
provenance can never hand a person's choice to a run, it can only protect a derived one.

When a person *changes* a derived value — the owner in the app or an operator in the admin panel —
the row is deleted and the value is theirs from then on. Saving a recipe without changing it (to fix
its title, say) keeps the row: that is not a decision about its diet.

---

## Recipe nutrition summary: a cache for ranking

`recipe_nutrition_summary` (`V24`) holds each recipe's nutrition as last computed, so a whole
cookbook can be ranked by calories without recomputing every recipe. Suggesting recipes and
planning a week read it; showing one recipe does not, and still computes on demand.

It is **a cache with a stated provenance, never a second source of truth**. Nothing ever edits a
row: anything that could move the numbers deletes it, and the next read computes it again.

| Column | Type | Meaning |
|---|---|---|
| `recipe_id` | bigint → `recipe` | Primary key, shared with the recipe. Cascades on delete |
| `per_serving_basis` | boolean | True where the values are per serving; false where the recipe has no servings and they are for the whole recipe |
| `energy_kcal` … `salt` | real | The nine nutrient values, in the same columns `catalogue_food` uses |
| `quality` | varchar(16) | `COMPLETE`, `INCOMPLETE` (shown with a warning) or `UNAVAILABLE` (too uncertain to show, or to rank on) |
| `warning_count` | integer | Lines that may move the values noticeably |
| `main_food_id` | bigint → `catalogue_food` | The ingredient contributing the most grams — what "another pasta dish" means when a week is spread over different foods. Set null if that food is deleted, rather than losing the row |
| `main_food_gram_share` | real | Its share of the weighed grams, 0 to 1 |
| `computed_at` | timestamptz | |
| `dataset_label` | varchar(64) | The dataset release the values were read from. A row from an older release is treated as missing |

When a row is dropped:

- **The recipe is saved** — through the app or the admin panel. `RecipeService` publishes a
  `RecipeChangedEvent`; it does not know the cache exists.
- **The catalogue changes** — a dataset import, a corrected custom food, a relink, a merge. Every
  row is dropped: working out which recipes a changed food affects costs more than recomputing the
  ones anybody actually asks for.
- **A new dataset ships** — rows carrying an older `dataset_label` are ignored on read, which also
  covers a row written before a restart onto a new release.

**A missing row therefore means "not computed yet", never "this recipe has no nutrition".**

---

## Weekplan generation (`V25`)

A cook answers the planning wizard once and keeps the answers as a **profile**; each "plan my week"
makes a **draft** from it. A draft is a proposal: it reaches the weekplan only when the cook accepts
it, and accepting adds meals to the days without removing anything planned by hand.

### `planning_profile`

| Column | Type | Meaning |
|---|---|---|
| `id` | bigint | From `planning_profile_seq` |
| `owner_user_id` | bigint → `cookpal_user` | Cascades on delete |
| `name` | varchar(255) | "Normale Woche", "Low Carb" |
| `default_profile` | boolean | The one the wizard opens with; the service keeps exactly one per cook, and the first profile is it |
| `household_size` | integer | How many people eat; a meal is planned for this many servings. Default 2 |
| `diet` | varchar(16) | **Hard filter**: a recipe must be this diet or less restrictive, and an unclassified recipe never passes. Null means no restriction |
| `meat_meals_per_week` | integer | Soft budget; null means no limit. Fish counts as meat |
| `kcal_per_day` | integer | Soft; what the meals with no target of their own share evenly, after those that have one. Null leaves calories out |
| `macro_style` | varchar(16) | `BALANCED`, `LOW_CARB`, `LOW_FAT`, `HIGH_PROTEIN`; soft |
| `cooldown_weeks` | integer | A recipe in the saved weekplan within this many weeks is not suggested again, unless the cookbook has nothing else. Default 2 |
| `leftovers_allowed` | boolean | Whether a recipe that makes enough is eaten again the next day. Default true |
| `spread_variety` | boolean | Keep the same main food and recipe group off neighbouring days. Default true |
| `created_on`, `last_change` | timestamptz | From `AuditableEntity` |

Only the diet and avoided ingredients are hard filters. Everything else is scored, so a real
cookbook always yields a plan with a named compromise rather than an empty week.

### `planning_profile_meal`

One row per meal of the day that is planned at all; a meal without a row is never planned.

| Column | Type | Meaning |
|---|---|---|
| `profile_id` | bigint → `planning_profile` | Cascades on delete |
| `meal_type` | varchar(16) | As in `recipe_meal_type` |
| `schedule` | varchar(160) | The weekdays it is cooked, each with its effort — `SIMPLE`, `ANY` or `ELABORATE`, judged against the cook's own cookbook — e.g. `MONDAY:SIMPLE,SATURDAY:ELABORATE`. On the other days the meal is a **gap** the cook fills themselves (a Butterbrot, the canteen, takeaway). The app fills the days from a weekly count, weekend first, unless the cook picks them |
| `target_kcal` | integer | Per serving. Null takes an even share of what `kcal_per_day` leaves after the meals with a target of their own |

### `planning_profile_pantry` and `planning_profile_avoided`

| Column | Type | Meaning |
|---|---|---|
| `planning_profile_pantry.ingredient_id` | bigint → `ingredient` | Something the cook has and wants used up |
| `planning_profile_pantry.amount`, `unit` | real, varchar(32) | How much. Where it resolves to grams, the plan spends it as a budget; without an amount it is worth one recipe. Once used up, further recipes using it are mildly penalised — the plan uses it up rather than serving it all week |
| `planning_profile_avoided.ingredient_id` | bigint → `ingredient` | **Hard filter**: never planned, variants included. Where any ingredient is avoided, a recipe with an unlinked ingredient is excluded too, because it cannot be shown to be free of it |

Both cascade when the ingredient or the profile is deleted.

### `plan_draft`

| Column | Type | Meaning |
|---|---|---|
| `id` | bigint | From `plan_draft_seq` |
| `owner_user_id` | bigint → `cookpal_user` | Cascades on delete |
| `profile_id` | bigint → `planning_profile` | The answers it was made from. Set null when the profile is deleted — the service first discards the profile's open drafts, so only closed ones outlive it |
| `start_date`, `days` | date, integer | The period; 1–14 days |
| `seed` | bigint | Makes the draft reproducible; drawing everything again is a new seed |
| `status` | varchar(16) | `DRAFT` (open), `ACCEPTED` or `DISCARDED`. Only an open draft can be changed |

### `plan_draft_slot`

One row per meal of the period.

| Column | Type | Meaning |
|---|---|---|
| `id` | bigint | From `plan_draft_slot_seq` |
| `draft_id` | bigint → `plan_draft` | Cascades on delete; indexed |
| `plan_date`, `meal_type` | date, varchar(16) | Which meal |
| `kind` | varchar(16) | `COOKED`, `LEFTOVER` (eaten from another slot's cooking) or `GAP` (left to the cook; never written to the weekplan) |
| `recipe_id` | bigint → `recipe` | Null for a gap, and for a meal the cookbook had nothing for. Set null if the recipe is deleted |
| `servings` | integer | What to cook: the household, doubled where the next day eats the leftovers |
| `leftover_of_id` | bigint → `plan_draft_slot` | For a leftover, where it is cooked. Cascades, since no cooking means no leftovers |
| `locked` | boolean | Kept as it is when the rest of the draft is drawn again |

Two `CHECK`s keep slots honest: a gap has no recipe, and a leftover always names its origin.

### `plan_draft_slot_term`

Why each recipe was placed, term by term (`cooldown`, `effortFit`, `pantry`, …), so the draft can
explain itself. The key is stable and the client writes the sentence in the reader's language.

| Column | Type | Meaning |
|---|---|---|
| `slot_id` | bigint → `plan_draft_slot` | Cascades on delete |
| `term` | varchar(32) | The term's key |
| `term_value` | double | Its signed contribution to the score |

---

## Cascades in the mapping as well

Every foreign key added in `V21`–`V25` declares its `ON DELETE` rule twice: in the migration, and as
`@OnDelete` on the entity. Integration tests build their schema from the entities (`ddl-auto:
create`), so without the annotation a deletion that relies on a cascade — deleting a recipe that
was passed over, deleting an account with planning profiles — would work in production and fail in
tests, or the other way round.
