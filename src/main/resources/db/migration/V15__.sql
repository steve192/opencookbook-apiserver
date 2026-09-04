-- Whether a scan still counts against its owner's daily allowance.
--
-- An operator resetting somebody's allowance clears this rather than deleting the jobs, so the
-- history of what was run stays intact and only the counting changes.

ALTER TABLE public.ml_job
    ADD COLUMN IF NOT EXISTS counts_towards_quota boolean DEFAULT true NOT NULL;
