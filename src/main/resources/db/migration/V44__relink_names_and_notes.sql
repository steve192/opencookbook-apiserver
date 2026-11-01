-- A relink run may also read recipe lines again as written, name and note, with today's catalogue: a note that now
-- names the food joins the name ("Tomaten" with "gestückelt"), and a name part that does not leaves it.

ALTER TABLE public.ingredient_relink_run DROP CONSTRAINT ingredient_relink_run_known_scope;
ALTER TABLE public.ingredient_relink_run ADD CONSTRAINT ingredient_relink_run_known_scope
    CHECK (scope IN ('NEVER_MATCHED_OR_UNLINKED', 'AUTOMATIC_BELOW_CONFIDENCE', 'ALL_AUTOMATIC', 'RETIRED_FOODS',
                     'OLDER_MATCHER', 'NAMES_AND_NOTES'));

-- A NAME_AND_NOTE proposal moves its lines from the name and note they share to the new ones.
ALTER TABLE public.ingredient_relink_proposal
    ADD COLUMN note character varying(500),
    ADD COLUMN new_name character varying(512),
    ADD COLUMN new_note character varying(500);
ALTER TABLE public.ingredient_relink_proposal DROP CONSTRAINT ingredient_relink_proposal_known_change;
ALTER TABLE public.ingredient_relink_proposal ADD CONSTRAINT ingredient_relink_proposal_known_change
    CHECK (change IN ('NEW_LINK', 'CHANGED', 'UNLINKED', 'CONFIDENCE', 'NAME_AND_NOTE'));

-- The lines of a NAME_AND_NOTE proposal, all written as its name and note at the preview: a line changed since is
-- skipped when applying, and a revert moves applied lines back to them.
CREATE TABLE public.ingredient_relink_line
(
    proposal_id bigint NOT NULL,
    need_id bigint NOT NULL,
    applied boolean NOT NULL DEFAULT false,
    CONSTRAINT ingredient_relink_line_pkey PRIMARY KEY (proposal_id, need_id),
    CONSTRAINT ingredient_relink_line_proposal_fkey FOREIGN KEY (proposal_id)
        REFERENCES public.ingredient_relink_proposal (id) ON DELETE CASCADE,
    CONSTRAINT ingredient_relink_line_need_fkey FOREIGN KEY (need_id)
        REFERENCES public.ingredient_need (id) ON DELETE CASCADE
);

-- KEEP_AS_WRITTEN: never read a line written so ("salz, pfeffer") into another name and note.
ALTER TABLE public.catalogue_name_rule DROP CONSTRAINT catalogue_name_rule_known_kind;
ALTER TABLE public.catalogue_name_rule ADD CONSTRAINT catalogue_name_rule_known_kind
    CHECK (kind IN ('NEVER_LINK_TO', 'NOT_A_FOOD', 'KEEP_AS_WRITTEN'));
