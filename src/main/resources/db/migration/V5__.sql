ALTER TABLE IF EXISTS public.cookpal_user
  ADD roles character varying(255) COLLATE pg_catalog."default";

ALTER TABLE IF EXISTS public.cookpal_user
    ADD CONSTRAINT cookpal_user_roles_check CHECK (roles::text = 'ADMIN'::text);