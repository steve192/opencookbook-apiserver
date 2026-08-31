-- WeekplanDay.recipes gained an @OrderColumn. The app can reorder the meals of a day, and a
-- JPA bag gives no guarantee that a list order survives a round trip, so the new order was
-- written but read back in whatever order the database happened to return.
ALTER TABLE public.weekplan_day_recipes
    ADD COLUMN IF NOT EXISTS recipe_order integer;

-- Existing days keep the order they have been coming back in. Nothing in the join table
-- records when an entry was added, so physical row order is the closest thing to the order
-- users have been seeing, with the id as a deterministic tie break.
UPDATE public.weekplan_day_recipes wdr
SET recipe_order = ordered.position
FROM (
    SELECT wdr2.weekplan_day_id,
           wdr2.recipes_id,
           row_number() OVER (
               PARTITION BY wdr2.weekplan_day_id
               ORDER BY wdr2.ctid, wdr2.recipes_id
           ) - 1 AS position
    FROM public.weekplan_day_recipes wdr2
) AS ordered
WHERE wdr.weekplan_day_id = ordered.weekplan_day_id
  AND wdr.recipes_id = ordered.recipes_id;

ALTER TABLE public.weekplan_day_recipes
    ALTER COLUMN recipe_order SET NOT NULL;
