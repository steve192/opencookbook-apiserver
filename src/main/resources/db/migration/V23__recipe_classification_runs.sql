-- Recipe classification: reviewed runs that derive what a recipe is, and which values they derived.
-- The values themselves stay where they always were (recipe.recipe_type); this is only provenance.

CREATE SEQUENCE IF NOT EXISTS public.recipe_classification_run_seq INCREMENT 1 START 1 MINVALUE 1 CACHE 1;
CREATE SEQUENCE IF NOT EXISTS public.recipe_classification_proposal_seq INCREMENT 1 START 1 MINVALUE 1 CACHE 1;
CREATE SEQUENCE IF NOT EXISTS public.derived_classification_seq INCREMENT 1 START 1 MINVALUE 1 CACHE 1;

CREATE TABLE public.recipe_classification_run
(
    id bigint NOT NULL,
    kind character varying(16) NOT NULL,
    scope character varying(32) NOT NULL,
    status character varying(16) NOT NULL,
    basis character varying(64),
    proposal_count integer NOT NULL DEFAULT 0,
    unreadable_count integer NOT NULL DEFAULT 0,
    -- Accepted, but changed since the preview and so left alone by apply.
    skipped_count integer NOT NULL DEFAULT 0,
    applied_count integer NOT NULL DEFAULT 0,
    started_by_user_id bigint,
    applied_at timestamp(6) with time zone,
    reverted_at timestamp(6) with time zone,
    created_on timestamp(6) with time zone,
    last_change timestamp(6) with time zone,
    CONSTRAINT recipe_classification_run_pkey PRIMARY KEY (id),
    CONSTRAINT recipe_classification_run_user_fkey FOREIGN KEY (started_by_user_id)
        REFERENCES public.cookpal_user (user_id) ON DELETE SET NULL,
    CONSTRAINT recipe_classification_run_kind_known CHECK (kind IN ('DIET')),
    CONSTRAINT recipe_classification_run_status_known
        CHECK (status IN ('PREVIEWED', 'APPLIED', 'REVERTED', 'DISCARDED')),
    CONSTRAINT recipe_classification_run_scope_known CHECK (scope IN ('NEVER_CLASSIFIED', 'DERIVED_ONLY'))
);

-- Values are stored as the kind encodes them, so a new kind needs no new columns.
CREATE TABLE public.recipe_classification_proposal
(
    id bigint NOT NULL,
    run_id bigint NOT NULL,
    recipe_id bigint NOT NULL,
    proposed_value character varying(64),
    previous_value character varying(64),
    -- Set where the previous value was itself derived, so a revert restores its provenance too.
    previous_run_id bigint,
    decision character varying(16) NOT NULL,
    reason character varying(512),
    CONSTRAINT recipe_classification_proposal_pkey PRIMARY KEY (id),
    CONSTRAINT recipe_classification_proposal_run_fkey FOREIGN KEY (run_id)
        REFERENCES public.recipe_classification_run (id) ON DELETE CASCADE,
    CONSTRAINT recipe_classification_proposal_previous_run_fkey FOREIGN KEY (previous_run_id)
        REFERENCES public.recipe_classification_run (id) ON DELETE SET NULL,
    CONSTRAINT recipe_classification_proposal_recipe_fkey FOREIGN KEY (recipe_id)
        REFERENCES public.recipe (id) ON DELETE CASCADE,
    CONSTRAINT recipe_classification_proposal_decision_known
        CHECK (decision IN ('PENDING', 'ACCEPTED', 'REJECTED', 'SKIPPED'))
);

CREATE INDEX recipe_classification_proposal_run_idx ON public.recipe_classification_proposal (run_id);

-- A recipe's value of a kind that a run derived. A value without a row is a person's - or was set
-- before runs existed, which is the same thing - and no run ever touches it.
CREATE TABLE public.derived_classification
(
    id bigint NOT NULL,
    recipe_id bigint NOT NULL,
    kind character varying(16) NOT NULL,
    run_id bigint NOT NULL,
    CONSTRAINT derived_classification_pkey PRIMARY KEY (id),
    CONSTRAINT derived_classification_one_per_kind UNIQUE (recipe_id, kind),
    CONSTRAINT derived_classification_recipe_fkey FOREIGN KEY (recipe_id)
        REFERENCES public.recipe (id) ON DELETE CASCADE,
    CONSTRAINT derived_classification_run_fkey FOREIGN KEY (run_id)
        REFERENCES public.recipe_classification_run (id) ON DELETE CASCADE,
    CONSTRAINT derived_classification_kind_known CHECK (kind IN ('DIET'))
);

CREATE INDEX derived_classification_run_idx ON public.derived_classification (run_id);
