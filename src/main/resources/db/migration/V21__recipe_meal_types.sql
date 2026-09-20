-- Which meals a recipe suits. No row for a recipe means unknown, which stays eligible for every
-- filter except breakfast, where a wrong guess is the most visible.

CREATE TABLE public.recipe_meal_type
(
    recipe_id bigint NOT NULL,
    meal_type character varying(16) NOT NULL,
    CONSTRAINT recipe_meal_type_pkey PRIMARY KEY (recipe_id, meal_type),
    CONSTRAINT recipe_meal_type_recipe_fkey FOREIGN KEY (recipe_id)
        REFERENCES public.recipe (id) ON DELETE CASCADE,
    CONSTRAINT recipe_meal_type_known CHECK (meal_type IN ('BREAKFAST', 'LUNCH', 'DINNER', 'SNACK', 'DESSERT'))
);
