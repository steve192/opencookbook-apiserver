ALTER TABLE IF EXISTS public.recipe
    ADD COLUMN recipe_source character varying(255) COLLATE pg_catalog."default";