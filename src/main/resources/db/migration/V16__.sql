-- The language an account is written to in.
--
-- Nullable on purpose: an account that has never told us anything is a different thing from one
-- that asked for English, and only the first should follow a later change of default. Held as a
-- plain language tag rather than a constrained type so that adding a third translation is a
-- resource bundle and nothing else.

ALTER TABLE public.cookpal_user
    ADD COLUMN IF NOT EXISTS language character varying(16);
