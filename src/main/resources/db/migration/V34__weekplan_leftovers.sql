-- How much a planned recipe is cooked for, and which day's cooking a meal of leftovers eats from.
ALTER TABLE public.weekplan_day_recipe ADD COLUMN servings integer;
ALTER TABLE public.weekplan_day_recipe ADD COLUMN leftover_of date;
ALTER TABLE public.weekplan_day_recipe ADD CONSTRAINT weekplan_day_recipe_servings_positive
    CHECK (servings IS NULL OR servings > 0);
-- Recipes planned so far are cooked as written, as new ones are.
UPDATE public.weekplan_day_recipe meal SET servings = GREATEST(1, COALESCE(recipe.servings, 0))
    FROM public.recipe recipe
    WHERE meal.recipe_id = recipe.id AND NOT meal.is_simple_recipe;
