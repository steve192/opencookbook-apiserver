ALTER TABLE IF EXISTS public.cookpal_user
    DROP CONSTRAINT cookpal_user_roles_check;


ALTER TABLE IF EXISTS public.cookpal_user
    ADD CONSTRAINT cookpal_user_roles_check CHECK (roles::text = ANY (ARRAY['ADMIN'::character varying, 'DEMO'::character varying]::text[]));