-- Whether the account has been through the first-run screen that asks for a display name. Existing
-- accounts have not, so they are asked on their next sign in like a new one.
ALTER TABLE public.cookpal_user ADD COLUMN onboarded boolean NOT NULL DEFAULT false;
