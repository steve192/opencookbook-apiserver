-- The three @OneToMany collections on Recipe and WeekplanDay were mapped without a mappedBy or
-- a @JoinColumn, so Hibernate put each of them in a join table of its own. Every one of those
-- tables carried a UNIQUE on the child id, which is the giveaway: a child can only ever belong
-- to one parent, so the parent belongs on the child as an ordinary foreign key. The join tables
-- cost a table and a join each and let a child exist with no parent and no way to tell.
--
-- Nothing is deleted here. Rows whose join entry is missing keep a null parent, which is what
-- the mapping allows and what the deletion jobs already look for.

-- Recipe.neededIngredients: ordered by id, so no order column is needed.
ALTER TABLE public.ingredient_need
    ADD COLUMN IF NOT EXISTS recipe_id bigint;

UPDATE public.ingredient_need need
SET recipe_id = line.recipe_id
FROM public.recipe_needed_ingredients line
WHERE line.needed_ingredients_id = need.id;

ALTER TABLE public.ingredient_need
    ADD CONSTRAINT fk_ingredient_need_recipe FOREIGN KEY (recipe_id)
        REFERENCES public.recipe (id);

CREATE INDEX IF NOT EXISTS idx_ingredient_need_recipe ON public.ingredient_need (recipe_id);

DROP TABLE public.recipe_needed_ingredients;

-- Recipe.images: the position decides which one is the title image, so it moves across too.
ALTER TABLE public.recipe_image
    ADD COLUMN IF NOT EXISTS recipe_id bigint,
    ADD COLUMN IF NOT EXISTS image_order integer;

UPDATE public.recipe_image image
SET recipe_id = link.recipe_id,
    image_order = link.image_order
FROM public.recipe_images link
WHERE link.images_uuid = image.uuid;

ALTER TABLE public.recipe_image
    ADD CONSTRAINT fk_recipe_image_recipe FOREIGN KEY (recipe_id)
        REFERENCES public.recipe (id);

CREATE INDEX IF NOT EXISTS idx_recipe_image_recipe ON public.recipe_image (recipe_id);

DROP TABLE public.recipe_images;

-- WeekplanDay.recipes. The child already has a recipe_id of its own, pointing at the recipe it
-- plans, so the parent column has to be named after the day rather than reuse that name.
ALTER TABLE public.weekplan_day_recipe
    ADD COLUMN IF NOT EXISTS weekplan_day_id bigint,
    ADD COLUMN IF NOT EXISTS recipe_order integer;

UPDATE public.weekplan_day_recipe planned
SET weekplan_day_id = link.weekplan_day_id,
    recipe_order = link.recipe_order
FROM public.weekplan_day_recipes link
WHERE link.recipes_id = planned.id;

ALTER TABLE public.weekplan_day_recipe
    ADD CONSTRAINT fk_weekplan_day_recipe_day FOREIGN KEY (weekplan_day_id)
        REFERENCES public.weekplan_day (id);

CREATE INDEX IF NOT EXISTS idx_weekplan_day_recipe_day ON public.weekplan_day_recipe (weekplan_day_id);

DROP TABLE public.weekplan_day_recipes;
