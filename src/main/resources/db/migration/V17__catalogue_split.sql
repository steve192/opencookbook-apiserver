-- Nutrition catalogue: reference foods move out of the ingredient table into a catalogue of their
-- own, and an ingredient becomes purely what a user named in a recipe, linked to a catalogue food.
--
-- Before this migration one table held both. Public rows were the reference foods, carrying
-- nutrients and alternative names; private rows pointed at them through alias_for_id; and a recipe
-- line could point at a public row directly. After it:
-- - every former public ingredient is a CUSTOM catalogue food "legacy-<id>", with its names and
--   nutrients (the shipped dataset later merges these into its own foods where names agree);
-- - every former alias is a link from the ingredient to that catalogue food;
-- - every recipe line points at an ingredient of its recipe's owner;
-- - an owner has one ingredient per name;
-- - nothing of the old model is left in the ingredient table.
--
-- One transaction: it either applies completely or leaves version 16 untouched.

-- ------------------------------------------------------------------------------------------ guards
-- The old model allowed states the new one cannot represent. They would be silently lost, so the
-- migration refuses to run on them and names what to look at.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM public.ingredient WHERE is_public_ingredient AND owner_user_id IS NOT NULL) THEN
        RAISE EXCEPTION 'V17: public ingredients with an owner exist: SELECT id, name FROM ingredient WHERE is_public_ingredient AND owner_user_id IS NOT NULL';
    END IF;
    IF EXISTS (SELECT 1 FROM public.ingredient WHERE NOT is_public_ingredient AND owner_user_id IS NULL) THEN
        RAISE EXCEPTION 'V17: private ingredients without an owner exist: SELECT id, name FROM ingredient WHERE NOT is_public_ingredient AND owner_user_id IS NULL';
    END IF;
    IF EXISTS (SELECT 1 FROM public.ingredient_alternative_names names
               JOIN public.ingredient ingredient ON ingredient.id = names.ingredient_id
               WHERE NOT ingredient.is_public_ingredient) THEN
        RAISE EXCEPTION 'V17: private ingredients carry alternative names, which the new model has no place for';
    END IF;
END $$;

-- ---------------------------------------------------------------------------- nameless ingredients
-- Ingredients with a blank name are what the old editor's always offered blank row left behind. Their
-- recipe lines say nothing a reader could use ("1 große" of nothing), and the new model requires a
-- name, so the lines go with their ingredients. How many is written to the migration's log.
DO $$
DECLARE
    deleted_lines integer;
    deleted_ingredients integer;
BEGIN
    CREATE TEMPORARY TABLE nameless_ingredient ON COMMIT DROP AS
        SELECT id FROM public.ingredient WHERE name IS NULL OR btrim(name) = '';

    DELETE FROM public.recipe_needed_ingredients line
    USING public.ingredient_need need
    WHERE line.needed_ingredients_id = need.id AND need.ingredient_id IN (SELECT id FROM nameless_ingredient);

    DELETE FROM public.ingredient_need WHERE ingredient_id IN (SELECT id FROM nameless_ingredient);
    GET DIAGNOSTICS deleted_lines = ROW_COUNT;

    DELETE FROM public.ingredient_alternative_names WHERE ingredient_id IN (SELECT id FROM nameless_ingredient);
    UPDATE public.ingredient SET alias_for_id = NULL WHERE alias_for_id IN (SELECT id FROM nameless_ingredient);
    DELETE FROM public.ingredient WHERE id IN (SELECT id FROM nameless_ingredient);
    GET DIAGNOSTICS deleted_ingredients = ROW_COUNT;

    RAISE NOTICE 'V17: deleted % recipe lines of % ingredients without a name', deleted_lines, deleted_ingredients;
END $$;

-- --------------------------------------------------------------------------------------- catalogue
CREATE SEQUENCE IF NOT EXISTS public.catalogue_food_seq INCREMENT 1 START 1 MINVALUE 1 CACHE 1;

CREATE TABLE public.catalogue_food
(
    id bigint NOT NULL,
    created_on timestamp(6) with time zone,
    last_change timestamp(6) with time zone,
    -- Stable across dataset releases: derived from the source code ("bls-K110100"), or
    -- "custom-..." and "legacy-..." for foods an administrator created.
    catalogue_key character varying(64) NOT NULL,
    origin character varying(16) NOT NULL,
    variant_of_id bigint,
    retired boolean NOT NULL DEFAULT false,
    source_type character varying(8),
    source_code character varying(32),
    source_name character varying(512),
    negligible boolean NOT NULL DEFAULT false,
    density_g_per_ml real,
    energy_kcal real,
    energy_kj real,
    fat real,
    saturated_fat real,
    carbohydrates real,
    sugar real,
    fibre real,
    protein real,
    salt real,
    -- Only while this migration runs: which former public ingredient a legacy food came from.
    legacy_ingredient_id bigint,
    CONSTRAINT catalogue_food_pkey PRIMARY KEY (id),
    CONSTRAINT catalogue_food_key_unique UNIQUE (catalogue_key),
    CONSTRAINT catalogue_food_variant_of_fkey FOREIGN KEY (variant_of_id)
        REFERENCES public.catalogue_food (id) ON DELETE RESTRICT,
    CONSTRAINT catalogue_food_known_origin CHECK (origin IN ('DATASET', 'CUSTOM')),
    CONSTRAINT catalogue_food_dataset_has_source CHECK (origin <> 'DATASET' OR (source_type IS NOT NULL AND source_code IS NOT NULL))
);

CREATE TABLE public.catalogue_food_name
(
    catalogue_food_id bigint NOT NULL,
    language_iso_code character varying(8) NOT NULL,
    name character varying(512) NOT NULL,
    display boolean NOT NULL,
    origin character varying(16) NOT NULL,
    CONSTRAINT catalogue_food_name_food_fkey FOREIGN KEY (catalogue_food_id)
        REFERENCES public.catalogue_food (id) ON DELETE CASCADE,
    CONSTRAINT catalogue_food_name_known_origin CHECK (origin IN ('DATASET', 'ADMIN'))
);

-- A name identifies one food, whatever its capitalisation. Enforced here because names are added
-- by the dataset import and by administrators independently of each other.
CREATE UNIQUE INDEX catalogue_food_name_unique
    ON public.catalogue_food_name (language_iso_code, lower(name));
CREATE INDEX catalogue_food_name_food
    ON public.catalogue_food_name (catalogue_food_id);

CREATE TABLE public.catalogue_food_state
(
    catalogue_food_id bigint NOT NULL,
    state_key character varying(32) NOT NULL,
    CONSTRAINT catalogue_food_state_pkey PRIMARY KEY (catalogue_food_id, state_key),
    CONSTRAINT catalogue_food_state_food_fkey FOREIGN KEY (catalogue_food_id)
        REFERENCES public.catalogue_food (id) ON DELETE CASCADE
);

CREATE TABLE public.catalogue_food_portion
(
    catalogue_food_id bigint NOT NULL,
    unit_key character varying(32) NOT NULL,
    grams real NOT NULL,
    origin character varying(16) NOT NULL,
    CONSTRAINT catalogue_food_portion_pkey PRIMARY KEY (catalogue_food_id, unit_key),
    CONSTRAINT catalogue_food_portion_food_fkey FOREIGN KEY (catalogue_food_id)
        REFERENCES public.catalogue_food (id) ON DELETE CASCADE,
    CONSTRAINT catalogue_food_portion_weighs_something CHECK (grams > 0),
    CONSTRAINT catalogue_food_portion_known_origin CHECK (origin IN ('SOURCE', 'ESTIMATED', 'ADMIN'))
);

-- Which dataset release an instance has imported. The checksum identifies the content; a row being
-- inserted is also how two instances starting together agree on which one imports.
CREATE TABLE public.nutrition_dataset_import
(
    checksum character varying(64) NOT NULL,
    label character varying(64) NOT NULL,
    status character varying(16) NOT NULL,
    started_at timestamp(6) with time zone NOT NULL,
    finished_at timestamp(6) with time zone,
    report text,
    CONSTRAINT nutrition_dataset_import_pkey PRIMARY KEY (checksum),
    CONSTRAINT nutrition_dataset_import_known_status CHECK (status IN ('RUNNING', 'DONE', 'FAILED'))
);

-- ---------------------------------------------------------- former public ingredients -> catalogue
INSERT INTO public.catalogue_food (id, created_on, last_change, catalogue_key, origin, energy_kcal, fat, saturated_fat,
                                   carbohydrates, sugar, protein, salt, legacy_ingredient_id)
SELECT nextval('public.catalogue_food_seq'), ingredient.created_on, ingredient.last_change, 'legacy-' || ingredient.id,
       'CUSTOM', ingredient.nutrients_energy, ingredient.nutrients_fat, ingredient.nutrients_saturated_fat,
       ingredient.nutrients_carbohydrates, ingredient.nutrients_sugar, ingredient.nutrients_protein,
       ingredient.nutrients_salt, ingredient.id
FROM public.ingredient ingredient
WHERE ingredient.is_public_ingredient
ORDER BY ingredient.id;

-- Names in order of the food they belong to, so where two former public ingredients shared a name
-- the older one keeps it. Former public ingredients were named in German.
INSERT INTO public.catalogue_food_name (catalogue_food_id, language_iso_code, name, display, origin)
SELECT food.id, named.language_iso_code, named.name, named.display, 'ADMIN'
FROM (
    SELECT ingredient.id AS ingredient_id, 'de' AS language_iso_code, btrim(ingredient.name) AS name, true AS display, 0 AS position
    FROM public.ingredient ingredient
    WHERE ingredient.is_public_ingredient
    UNION ALL
    SELECT names.ingredient_id, names.language_iso_code, btrim(names.alternative_name), false, 1
    FROM public.ingredient_alternative_names names
    WHERE names.alternative_name IS NOT NULL AND btrim(names.alternative_name) <> ''
) named
JOIN public.catalogue_food food ON food.legacy_ingredient_id = named.ingredient_id
ORDER BY food.id, named.position
ON CONFLICT (language_iso_code, lower(name)) DO NOTHING;

-- ------------------------------------------------------------------------------ ingredient links
ALTER TABLE public.ingredient
    ADD COLUMN catalogue_food_id bigint,
    ADD COLUMN link_source character varying(8),
    ADD COLUMN link_confidence real,
    ADD COLUMN link_matcher_version integer,
    ADD COLUMN linked_at timestamp(6) with time zone,
    ADD COLUMN excluded_from_nutrition boolean NOT NULL DEFAULT false;

-- Former aliases. Matcher version 0 marks links the old fuzzy matcher made.
UPDATE public.ingredient ingredient
SET catalogue_food_id = food.id, link_source = 'AUTO', link_matcher_version = 0, linked_at = now()
FROM public.catalogue_food food
WHERE food.legacy_ingredient_id = ingredient.alias_for_id
  AND NOT ingredient.is_public_ingredient;

-- ------------------------------------------------- recipe lines pointing at a public ingredient
-- Lines no recipe owns are left over from earlier bugs and belong to nobody.
DELETE FROM public.ingredient_need need
WHERE NOT EXISTS (SELECT 1 FROM public.recipe_needed_ingredients line WHERE line.needed_ingredients_id = need.id);

-- An owner's own ingredient takes the public ingredient's place. Where the owner has none of that
-- name yet, it is created, linked to the catalogue food the public ingredient became.
INSERT INTO public.ingredient (id, created_on, last_change, is_public_ingredient, name, owner_user_id,
                               catalogue_food_id, link_source, link_matcher_version, linked_at)
SELECT nextval('public.ingredient_seq'), now(), now(), false, users.name, users.owner_user_id, users.catalogue_food_id,
       'AUTO', 0, now()
FROM (
    SELECT DISTINCT recipe.owner_user_id, public_ingredient.name, food.id AS catalogue_food_id
    FROM public.ingredient_need need
    JOIN public.recipe_needed_ingredients line ON line.needed_ingredients_id = need.id
    JOIN public.recipe recipe ON recipe.id = line.recipe_id
    JOIN public.ingredient public_ingredient ON public_ingredient.id = need.ingredient_id AND public_ingredient.is_public_ingredient
    JOIN public.catalogue_food food ON food.legacy_ingredient_id = public_ingredient.id
    WHERE NOT EXISTS (SELECT 1 FROM public.ingredient own
                      WHERE NOT own.is_public_ingredient AND own.owner_user_id = recipe.owner_user_id AND own.name = public_ingredient.name)
) users;

-- An own ingredient of that name that was not linked yet gets the public ingredient's food, as the
-- old exact-name lookup would have given it.
UPDATE public.ingredient own
SET catalogue_food_id = food.id, link_source = 'AUTO', link_matcher_version = 0, linked_at = now()
FROM public.ingredient public_ingredient
JOIN public.catalogue_food food ON food.legacy_ingredient_id = public_ingredient.id
WHERE public_ingredient.is_public_ingredient
  AND NOT own.is_public_ingredient
  AND own.catalogue_food_id IS NULL
  AND own.name = public_ingredient.name;

UPDATE public.ingredient_need need
SET ingredient_id = own.id
FROM public.recipe_needed_ingredients line, public.recipe recipe, public.ingredient public_ingredient, public.ingredient own
WHERE line.needed_ingredients_id = need.id
  AND recipe.id = line.recipe_id
  AND public_ingredient.id = need.ingredient_id AND public_ingredient.is_public_ingredient
  AND own.owner_user_id = recipe.owner_user_id AND NOT own.is_public_ingredient AND own.name = public_ingredient.name;

-- --------------------------------------------- recipe lines pointing at somebody else's ingredient
-- Older copies of recipes kept pointing at the original owner's ingredients, so one user's decision
-- about an ingredient would change another user's recipe. The recipe's owner gets an ingredient of
-- that name of their own - linked to the same food, as the matcher would have it, not as the other
-- user decided it.
INSERT INTO public.ingredient (id, created_on, last_change, is_public_ingredient, name, owner_user_id,
                               catalogue_food_id, link_source, link_matcher_version, linked_at)
SELECT nextval('public.ingredient_seq'), now(), now(), false, users.name, users.owner_user_id, users.catalogue_food_id,
       CASE WHEN users.catalogue_food_id IS NULL THEN NULL ELSE 'AUTO' END,
       CASE WHEN users.catalogue_food_id IS NULL THEN NULL ELSE 0 END,
       CASE WHEN users.catalogue_food_id IS NULL THEN NULL ELSE now() END
FROM (
    SELECT DISTINCT ON (recipe.owner_user_id, other.name) recipe.owner_user_id, other.name, other.catalogue_food_id
    FROM public.ingredient_need need
    JOIN public.recipe_needed_ingredients line ON line.needed_ingredients_id = need.id
    JOIN public.recipe recipe ON recipe.id = line.recipe_id
    JOIN public.ingredient other ON other.id = need.ingredient_id
        AND NOT other.is_public_ingredient AND other.owner_user_id <> recipe.owner_user_id
    WHERE NOT EXISTS (SELECT 1 FROM public.ingredient own
                      WHERE NOT own.is_public_ingredient AND own.owner_user_id = recipe.owner_user_id AND own.name = other.name)
    ORDER BY recipe.owner_user_id, other.name, (other.catalogue_food_id IS NULL), other.id
) users;

UPDATE public.ingredient_need need
SET ingredient_id = own.id
FROM public.recipe_needed_ingredients line, public.recipe recipe, public.ingredient other, public.ingredient own
WHERE line.needed_ingredients_id = need.id
  AND recipe.id = line.recipe_id
  AND other.id = need.ingredient_id AND NOT other.is_public_ingredient AND other.owner_user_id <> recipe.owner_user_id
  AND own.id = (SELECT min(candidate.id) FROM public.ingredient candidate
                WHERE NOT candidate.is_public_ingredient AND candidate.owner_user_id = recipe.owner_user_id
                  AND candidate.name = other.name);

-- ------------------------------------------------------------- one ingredient per owner and name
-- Of several, the linked one stays, then the oldest; lines of the others move to it.
CREATE TEMPORARY TABLE duplicate_ingredient ON COMMIT DROP AS
SELECT id, kept
FROM (
    SELECT id, first_value(id) OVER (PARTITION BY owner_user_id, name ORDER BY (catalogue_food_id IS NULL), id) AS kept
    FROM public.ingredient
    WHERE NOT is_public_ingredient
) ranked
WHERE id <> kept;

UPDATE public.ingredient_need need
SET ingredient_id = duplicate.kept
FROM duplicate_ingredient duplicate
WHERE need.ingredient_id = duplicate.id;

DELETE FROM public.ingredient ingredient
USING duplicate_ingredient duplicate
WHERE ingredient.id = duplicate.id;

-- ----------------------------------------------------------------------- remove the old model
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM public.ingredient_need need
               JOIN public.ingredient ingredient ON ingredient.id = need.ingredient_id
               WHERE ingredient.is_public_ingredient) THEN
        RAISE EXCEPTION 'V17: recipe lines still point at public ingredients after moving them to their owners';
    END IF;
    IF EXISTS (SELECT 1 FROM public.ingredient_need need
               JOIN public.recipe_needed_ingredients line ON line.needed_ingredients_id = need.id
               JOIN public.recipe recipe ON recipe.id = line.recipe_id
               JOIN public.ingredient ingredient ON ingredient.id = need.ingredient_id
               WHERE ingredient.owner_user_id <> recipe.owner_user_id) THEN
        RAISE EXCEPTION 'V17: recipe lines still point at ingredients of another user after moving them to their owners';
    END IF;
END $$;

ALTER TABLE public.ingredient DROP CONSTRAINT IF EXISTS fkpksl6g80n423tap734mprav1v;
ALTER TABLE public.ingredient DROP COLUMN alias_for_id;
DROP TABLE public.ingredient_alternative_names;
DROP SEQUENCE IF EXISTS public.ingredient_alternativenames_seq;

DELETE FROM public.ingredient WHERE is_public_ingredient;

ALTER TABLE public.ingredient
    DROP COLUMN is_public_ingredient,
    DROP COLUMN nutrients_energy,
    DROP COLUMN nutrients_fat,
    DROP COLUMN nutrients_saturated_fat,
    DROP COLUMN nutrients_carbohydrates,
    DROP COLUMN nutrients_sugar,
    DROP COLUMN nutrients_protein,
    DROP COLUMN nutrients_salt,
    ALTER COLUMN name SET NOT NULL,
    ALTER COLUMN owner_user_id SET NOT NULL,
    ADD CONSTRAINT ingredient_owner_name_unique UNIQUE (owner_user_id, name),
    ADD CONSTRAINT ingredient_catalogue_food_fkey FOREIGN KEY (catalogue_food_id)
        REFERENCES public.catalogue_food (id) ON DELETE RESTRICT,
    ADD CONSTRAINT ingredient_known_link_source CHECK (link_source IS NULL OR link_source IN ('AUTO', 'USER', 'ADMIN')),
    ADD CONSTRAINT ingredient_excluded_has_no_link CHECK (NOT excluded_from_nutrition OR catalogue_food_id IS NULL);

CREATE INDEX ingredient_catalogue_food ON public.ingredient (catalogue_food_id);

ALTER TABLE public.catalogue_food DROP COLUMN legacy_ingredient_id;
