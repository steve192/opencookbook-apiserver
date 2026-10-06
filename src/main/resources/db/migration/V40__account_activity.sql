-- When an account was last used and signed in to, and how far it is on its way to deletion for disuse.
-- A null last_active_at means not used since signing up.
ALTER TABLE public.cookpal_user
    ADD COLUMN last_active_at timestamp(6) with time zone,
    ADD COLUMN last_sign_in_at timestamp(6) with time zone,
    ADD COLUMN inactivity_notices integer NOT NULL DEFAULT 0,
    ADD COLUMN last_inactivity_notice_at timestamp(6) with time zone,
    ADD COLUMN google_linked boolean NOT NULL DEFAULT false;

-- Existing accounts start their countdown now rather than at a sign in nobody recorded.
UPDATE public.cookpal_user SET last_active_at = now();

-- Only a Google sign in creates an account without a password.
UPDATE public.cookpal_user SET google_linked = true WHERE password_hash IS NULL;
