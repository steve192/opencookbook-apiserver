-- What a piece of an ingredient weighs for its owner, where the catalogue does not know or the owner
-- knows better: "1 Stück wiegt bei mir 200 g". One weight per ingredient and unit.

CREATE TABLE public.ingredient_portion_override
(
    ingredient_id bigint NOT NULL,
    -- The unit's key in the unit lexicon, e.g. piece, can, small_can.
    unit_key character varying(32) NOT NULL,
    grams real NOT NULL,
    CONSTRAINT ingredient_portion_override_pkey PRIMARY KEY (ingredient_id, unit_key),
    CONSTRAINT ingredient_portion_override_ingredient_fkey FOREIGN KEY (ingredient_id)
        REFERENCES public.ingredient (id) ON DELETE CASCADE,
    CONSTRAINT ingredient_portion_override_positive CHECK (grams > 0)
);
