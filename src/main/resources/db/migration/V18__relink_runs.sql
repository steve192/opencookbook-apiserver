-- Relinking ingredients to the catalogue under an administrator's review, and remembered decisions.
--
-- A relink run proposes, for every distinct ingredient name in its scope, which catalogue food the
-- matcher would link it to now. Proposals are decided, applied and, if need be, reverted. Rules keep a
-- rejected proposal from coming back.

-- ----------------------------------------------------------------------------------- name rules
CREATE SEQUENCE IF NOT EXISTS public.catalogue_name_rule_seq INCREMENT 1 START 1 MINVALUE 1 CACHE 1;

CREATE TABLE public.catalogue_name_rule
(
    id bigint NOT NULL,
    created_on timestamp(6) with time zone,
    last_change timestamp(6) with time zone,
    -- The ingredient name as matching compares names: trimmed, lower case, single spaces.
    name character varying(512) NOT NULL,
    -- NEVER_LINK_TO: never link this name to that food. NOT_A_FOOD: never link this name at all.
    kind character varying(16) NOT NULL,
    catalogue_food_id bigint,
    created_by_user_id bigint,
    CONSTRAINT catalogue_name_rule_pkey PRIMARY KEY (id),
    CONSTRAINT catalogue_name_rule_food_fkey FOREIGN KEY (catalogue_food_id)
        REFERENCES public.catalogue_food (id) ON DELETE CASCADE,
    CONSTRAINT catalogue_name_rule_user_fkey FOREIGN KEY (created_by_user_id)
        REFERENCES public.cookpal_user (user_id) ON DELETE SET NULL,
    CONSTRAINT catalogue_name_rule_known_kind CHECK (kind IN ('NEVER_LINK_TO', 'NOT_A_FOOD')),
    CONSTRAINT catalogue_name_rule_food_where_needed CHECK ((kind = 'NEVER_LINK_TO') = (catalogue_food_id IS NOT NULL))
);

-- One rule per name, kind and food.
CREATE UNIQUE INDEX catalogue_name_rule_unique
    ON public.catalogue_name_rule (name, kind, coalesce(catalogue_food_id, 0));

-- -------------------------------------------------------------------------------------- runs
CREATE SEQUENCE IF NOT EXISTS public.ingredient_relink_run_seq INCREMENT 1 START 1 MINVALUE 1 CACHE 1;

CREATE TABLE public.ingredient_relink_run
(
    id bigint NOT NULL,
    created_on timestamp(6) with time zone,
    last_change timestamp(6) with time zone,
    scope character varying(32) NOT NULL,
    -- For the scope of automatic links below a confidence.
    below_confidence real,
    matcher_version integer NOT NULL,
    dataset_label character varying(64),
    status character varying(16) NOT NULL,
    proposal_count integer NOT NULL,
    ingredient_count integer NOT NULL,
    unchanged_count integer NOT NULL,
    applied_count integer NOT NULL DEFAULT 0,
    skipped_count integer NOT NULL DEFAULT 0,
    started_by_user_id bigint,
    applied_at timestamp(6) with time zone,
    reverted_at timestamp(6) with time zone,
    CONSTRAINT ingredient_relink_run_pkey PRIMARY KEY (id),
    CONSTRAINT ingredient_relink_run_user_fkey FOREIGN KEY (started_by_user_id)
        REFERENCES public.cookpal_user (user_id) ON DELETE SET NULL,
    CONSTRAINT ingredient_relink_run_known_scope CHECK (scope IN ('NEVER_MATCHED_OR_UNLINKED', 'AUTOMATIC_BELOW_CONFIDENCE',
                                                                  'ALL_AUTOMATIC', 'RETIRED_FOODS', 'OLDER_MATCHER')),
    CONSTRAINT ingredient_relink_run_known_status CHECK (status IN ('PREVIEWED', 'APPLIED', 'REVERTED', 'DISCARDED'))
);

-- ------------------------------------------------------------------------------------ proposals
CREATE SEQUENCE IF NOT EXISTS public.ingredient_relink_proposal_seq INCREMENT 1 START 1 MINVALUE 1 CACHE 1;

CREATE TABLE public.ingredient_relink_proposal
(
    id bigint NOT NULL,
    run_id bigint NOT NULL,
    -- The ingredients a proposal is for share this name, their owners' language and their current food.
    name character varying(512) NOT NULL,
    language character varying(16),
    old_food_id bigint,
    new_food_id bigint,
    new_confidence real,
    change character varying(16) NOT NULL,
    decision character varying(16) NOT NULL,
    CONSTRAINT ingredient_relink_proposal_pkey PRIMARY KEY (id),
    CONSTRAINT ingredient_relink_proposal_run_fkey FOREIGN KEY (run_id)
        REFERENCES public.ingredient_relink_run (id) ON DELETE CASCADE,
    CONSTRAINT ingredient_relink_proposal_old_food_fkey FOREIGN KEY (old_food_id)
        REFERENCES public.catalogue_food (id) ON DELETE SET NULL,
    CONSTRAINT ingredient_relink_proposal_new_food_fkey FOREIGN KEY (new_food_id)
        REFERENCES public.catalogue_food (id) ON DELETE SET NULL,
    CONSTRAINT ingredient_relink_proposal_known_change CHECK (change IN ('NEW_LINK', 'CHANGED', 'UNLINKED')),
    CONSTRAINT ingredient_relink_proposal_known_decision CHECK (decision IN ('PENDING', 'ACCEPTED', 'REJECTED'))
);

CREATE INDEX ingredient_relink_proposal_run ON public.ingredient_relink_proposal (run_id);

-- The ingredients of a proposal, with their link as it was at the preview: an ingredient changed since
-- is skipped when applying, and a revert restores exactly these values.
CREATE TABLE public.ingredient_relink_member
(
    proposal_id bigint NOT NULL,
    ingredient_id bigint NOT NULL,
    previewed_last_change timestamp(6) with time zone,
    old_food_id bigint,
    old_link_source character varying(8),
    old_link_confidence real,
    old_link_matcher_version integer,
    old_linked_at timestamp(6) with time zone,
    applied boolean NOT NULL DEFAULT false,
    CONSTRAINT ingredient_relink_member_pkey PRIMARY KEY (proposal_id, ingredient_id),
    CONSTRAINT ingredient_relink_member_proposal_fkey FOREIGN KEY (proposal_id)
        REFERENCES public.ingredient_relink_proposal (id) ON DELETE CASCADE,
    CONSTRAINT ingredient_relink_member_ingredient_fkey FOREIGN KEY (ingredient_id)
        REFERENCES public.ingredient (id) ON DELETE CASCADE,
    CONSTRAINT ingredient_relink_member_old_food_fkey FOREIGN KEY (old_food_id)
        REFERENCES public.catalogue_food (id) ON DELETE SET NULL
);

CREATE INDEX ingredient_relink_member_ingredient ON public.ingredient_relink_member (ingredient_id);

-- ---------------------------------------------------------------------------------- ingredients
-- The run that last linked an ingredient; cleared when the owner or an administrator decides instead.
ALTER TABLE public.ingredient
    ADD COLUMN link_run_id bigint,
    ADD CONSTRAINT ingredient_link_run_fkey FOREIGN KEY (link_run_id)
        REFERENCES public.ingredient_relink_run (id) ON DELETE SET NULL;
