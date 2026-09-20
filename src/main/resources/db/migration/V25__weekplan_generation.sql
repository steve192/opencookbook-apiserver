-- Generating weekplans: a cook's saved answers (profiles) and the proposed weeks made from them
-- (drafts). A draft reaches the weekplan only when the cook accepts it.

CREATE SEQUENCE IF NOT EXISTS public.planning_profile_seq INCREMENT 1 START 1 MINVALUE 1 CACHE 1;
CREATE SEQUENCE IF NOT EXISTS public.plan_draft_seq INCREMENT 1 START 1 MINVALUE 1 CACHE 1;
CREATE SEQUENCE IF NOT EXISTS public.plan_draft_slot_seq INCREMENT 1 START 1 MINVALUE 1 CACHE 1;

-- ------------------------------------------------------------------------------------- profiles
CREATE TABLE public.planning_profile
(
    id bigint NOT NULL,
    created_on timestamp(6) with time zone,
    last_change timestamp(6) with time zone,
    owner_user_id bigint NOT NULL,
    name character varying(255) NOT NULL,
    -- The one the wizard opens with.
    default_profile boolean NOT NULL DEFAULT false,
    household_size integer NOT NULL DEFAULT 2,
    -- A hard filter; null means no restriction.
    diet character varying(16),
    -- Soft limits; null means none.
    meat_meals_per_week integer,
    kcal_per_day integer,
    macro_style character varying(16),
    cooldown_weeks integer NOT NULL DEFAULT 2,
    leftovers_allowed boolean NOT NULL DEFAULT true,
    spread_variety boolean NOT NULL DEFAULT true,
    CONSTRAINT planning_profile_pkey PRIMARY KEY (id),
    CONSTRAINT planning_profile_owner_fkey FOREIGN KEY (owner_user_id)
        REFERENCES public.cookpal_user (user_id) ON DELETE CASCADE,
    CONSTRAINT planning_profile_diet_known CHECK (diet IS NULL OR diet IN ('VEGAN', 'VEGETARIAN', 'MEAT')),
    CONSTRAINT planning_profile_macro_known
        CHECK (macro_style IS NULL OR macro_style IN ('BALANCED', 'LOW_CARB', 'LOW_FAT', 'HIGH_PROTEIN')),
    CONSTRAINT planning_profile_household_positive CHECK (household_size > 0),
    CONSTRAINT planning_profile_cooldown_not_negative CHECK (cooldown_weeks >= 0)
);

-- How each meal of the day is planned. The days a meal is not cooked are gaps for the cook to fill.
CREATE TABLE public.planning_profile_meal
(
    profile_id bigint NOT NULL,
    meal_type character varying(16) NOT NULL,
    -- The weekdays it is cooked, each with its effort: "MONDAY:SIMPLE,SATURDAY:ELABORATE".
    schedule character varying(160) NOT NULL DEFAULT '',
    target_kcal integer,
    CONSTRAINT planning_profile_meal_profile_fkey FOREIGN KEY (profile_id)
        REFERENCES public.planning_profile (id) ON DELETE CASCADE,
    CONSTRAINT planning_profile_meal_known CHECK (meal_type IN ('BREAKFAST', 'LUNCH', 'DINNER', 'SNACK', 'DESSERT'))
);

-- What the cook has and wants used up; the amount makes it a budget the plan spends.
CREATE TABLE public.planning_profile_pantry
(
    profile_id bigint NOT NULL,
    ingredient_id bigint NOT NULL,
    amount real,
    unit character varying(32),
    CONSTRAINT planning_profile_pantry_profile_fkey FOREIGN KEY (profile_id)
        REFERENCES public.planning_profile (id) ON DELETE CASCADE,
    CONSTRAINT planning_profile_pantry_ingredient_fkey FOREIGN KEY (ingredient_id)
        REFERENCES public.ingredient (id) ON DELETE CASCADE
);

-- Never planned: allergies and dislikes, matched through the catalogue.
CREATE TABLE public.planning_profile_avoided
(
    profile_id bigint NOT NULL,
    ingredient_id bigint NOT NULL,
    CONSTRAINT planning_profile_avoided_pkey PRIMARY KEY (profile_id, ingredient_id),
    CONSTRAINT planning_profile_avoided_profile_fkey FOREIGN KEY (profile_id)
        REFERENCES public.planning_profile (id) ON DELETE CASCADE,
    CONSTRAINT planning_profile_avoided_ingredient_fkey FOREIGN KEY (ingredient_id)
        REFERENCES public.ingredient (id) ON DELETE CASCADE
);

-- --------------------------------------------------------------------------------------- drafts
CREATE TABLE public.plan_draft
(
    id bigint NOT NULL,
    created_on timestamp(6) with time zone,
    last_change timestamp(6) with time zone,
    owner_user_id bigint NOT NULL,
    profile_id bigint,
    start_date date NOT NULL,
    days integer NOT NULL,
    seed bigint NOT NULL,
    status character varying(16) NOT NULL,
    CONSTRAINT plan_draft_pkey PRIMARY KEY (id),
    CONSTRAINT plan_draft_owner_fkey FOREIGN KEY (owner_user_id)
        REFERENCES public.cookpal_user (user_id) ON DELETE CASCADE,
    -- Deleting a profile keeps the drafts made from it as history; the open ones are discarded first.
    CONSTRAINT plan_draft_profile_fkey FOREIGN KEY (profile_id)
        REFERENCES public.planning_profile (id) ON DELETE SET NULL,
    CONSTRAINT plan_draft_status_known CHECK (status IN ('DRAFT', 'ACCEPTED', 'DISCARDED')),
    CONSTRAINT plan_draft_days_positive CHECK (days > 0)
);

CREATE TABLE public.plan_draft_slot
(
    id bigint NOT NULL,
    draft_id bigint NOT NULL,
    plan_date date NOT NULL,
    meal_type character varying(16) NOT NULL,
    kind character varying(16) NOT NULL,
    recipe_id bigint,
    servings integer,
    leftover_of_id bigint,
    locked boolean NOT NULL DEFAULT false,
    CONSTRAINT plan_draft_slot_pkey PRIMARY KEY (id),
    CONSTRAINT plan_draft_slot_draft_fkey FOREIGN KEY (draft_id)
        REFERENCES public.plan_draft (id) ON DELETE CASCADE,
    -- A deleted recipe leaves its slot empty rather than breaking the draft.
    CONSTRAINT plan_draft_slot_recipe_fkey FOREIGN KEY (recipe_id)
        REFERENCES public.recipe (id) ON DELETE SET NULL,
    -- No cooking, no leftovers: removing the slot where it was cooked removes what it left over.
    CONSTRAINT plan_draft_slot_leftover_fkey FOREIGN KEY (leftover_of_id)
        REFERENCES public.plan_draft_slot (id) ON DELETE CASCADE,
    CONSTRAINT plan_draft_slot_meal_known CHECK (meal_type IN ('BREAKFAST', 'LUNCH', 'DINNER', 'SNACK', 'DESSERT')),
    CONSTRAINT plan_draft_slot_kind_known CHECK (kind IN ('COOKED', 'LEFTOVER', 'GAP')),
    -- A gap plans nothing, and a leftover always says what it is left over from.
    CONSTRAINT plan_draft_slot_gap_is_empty CHECK (kind <> 'GAP' OR recipe_id IS NULL),
    CONSTRAINT plan_draft_slot_leftover_has_origin CHECK (kind <> 'LEFTOVER' OR leftover_of_id IS NOT NULL)
);

CREATE INDEX plan_draft_slot_draft_idx ON public.plan_draft_slot (draft_id);

-- Why each recipe was placed, term by term, so the draft can explain itself.
CREATE TABLE public.plan_draft_slot_term
(
    slot_id bigint NOT NULL,
    term character varying(32) NOT NULL,
    term_value double precision NOT NULL,
    CONSTRAINT plan_draft_slot_term_slot_fkey FOREIGN KEY (slot_id)
        REFERENCES public.plan_draft_slot (id) ON DELETE CASCADE
);
