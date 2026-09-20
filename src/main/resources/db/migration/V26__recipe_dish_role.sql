-- What a recipe is when it is not a dish of its own: SIDE or COMPONENT. Null for a dish, which is
-- also what a recipe nobody marked counts as, so none disappears from planning for missing metadata.
ALTER TABLE public.recipe ADD COLUMN dish_role character varying(16);
ALTER TABLE public.recipe ADD CONSTRAINT recipe_dish_role_known
    CHECK (dish_role IS NULL OR dish_role IN ('SIDE', 'COMPONENT'));
