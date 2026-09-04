-- How far back in the subsystem's queue a scan is, as of the last time it was asked.
--
-- Kept so that waiting can say something better than "please wait". It is a snapshot rather
-- than a fact about the job, which is why it is nullable and why nothing reads it once the job
-- has finished.

ALTER TABLE public.ml_job
    ADD COLUMN IF NOT EXISTS queue_position integer;
