-- Recipe.images gained an @OrderColumn. Which image is the recipe's title image is decided
-- by its position in the list, and a JPA bag gives no guarantee that a list order survives
-- a round trip, so the order has to be stored explicitly.
ALTER TABLE public.recipe_images
    ADD COLUMN IF NOT EXISTS image_order integer;

-- Existing recipes keep their images in upload order, which is what the app showed so far,
-- so nobody's title image changes because of this migration.
UPDATE public.recipe_images ri
SET image_order = ordered.position
FROM (
    SELECT ri2.recipe_id,
           ri2.images_uuid,
           row_number() OVER (
               PARTITION BY ri2.recipe_id
               ORDER BY img.created_on NULLS FIRST, ri2.images_uuid
           ) - 1 AS position
    FROM public.recipe_images ri2
             JOIN public.recipe_image img ON img.uuid = ri2.images_uuid
) AS ordered
WHERE ri.recipe_id = ordered.recipe_id
  AND ri.images_uuid = ordered.images_uuid;

ALTER TABLE public.recipe_images
    ALTER COLUMN image_order SET NOT NULL;
