-- A relink proposal may also keep an ingredient's food and change only how sure the link is: the matcher
-- has become surer, or less sure, of the same food since it linked it.
ALTER TABLE public.ingredient_relink_proposal DROP CONSTRAINT ingredient_relink_proposal_known_change;
ALTER TABLE public.ingredient_relink_proposal ADD CONSTRAINT ingredient_relink_proposal_known_change
    CHECK (change IN ('NEW_LINK', 'CHANGED', 'UNLINKED', 'CONFIDENCE'));
