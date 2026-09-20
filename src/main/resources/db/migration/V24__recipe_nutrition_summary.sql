-- A recipe's nutrition as last computed, so ranking a whole cookbook does not recompute it.
--
-- A cache with a stated provenance, not a second source of truth: dataset_label and computed_at say
-- what produced the values, and anything that could change them deletes the row rather than editing
-- it. A missing row means "not computed yet", never "this recipe has no nutrition".

CREATE TABLE public.recipe_nutrition_summary
(
    recipe_id bigint NOT NULL,
    -- Per serving, or for the whole recipe where it has none; the flag says which.
    per_serving_basis boolean NOT NULL DEFAULT true,
    energy_kcal real,
    energy_kj real,
    fat real,
    saturated_fat real,
    carbohydrates real,
    sugar real,
    fibre real,
    protein real,
    salt real,
    quality character varying(16),
    warning_count integer NOT NULL DEFAULT 0,
    -- The ingredient contributing the most grams, for spreading a week over different foods.
    main_food_id bigint,
    main_food_gram_share real,
    computed_at timestamp(6) with time zone,
    dataset_label character varying(64),
    CONSTRAINT recipe_nutrition_summary_pkey PRIMARY KEY (recipe_id),
    CONSTRAINT recipe_nutrition_summary_recipe_fkey FOREIGN KEY (recipe_id)
        REFERENCES public.recipe (id) ON DELETE CASCADE,
    -- Set null rather than cascade: losing the main food must not lose the whole summary.
    CONSTRAINT recipe_nutrition_summary_food_fkey FOREIGN KEY (main_food_id)
        REFERENCES public.catalogue_food (id) ON DELETE SET NULL,
    CONSTRAINT recipe_nutrition_summary_quality_known
        CHECK (quality IS NULL OR quality IN ('COMPLETE', 'INCOMPLETE', 'UNAVAILABLE')),
    CONSTRAINT recipe_nutrition_summary_share_is_a_share
        CHECK (main_food_gram_share IS NULL OR (main_food_gram_share >= 0 AND main_food_gram_share <= 1))
);

-- Ranking a cookbook reads by quality and energy, so the rows worth ranking are found together.
CREATE INDEX recipe_nutrition_summary_quality_idx
    ON public.recipe_nutrition_summary (quality, energy_kcal);
