-- The machine learning subsystem: one row per piece of work handed to it.
--
-- The subsystem keeps its own copy. This one exists so that the app has something stable to
-- poll across a restart on either side, so that per-user quota can be counted, and so that a
-- failure can be explained rather than merely leaving a spinner turning.

CREATE TABLE IF NOT EXISTS public.ml_job
(
    id character varying(255) COLLATE pg_catalog."default" NOT NULL,
    created_on timestamp without time zone,
    last_change timestamp without time zone,
    job_type character varying(64) COLLATE pg_catalog."default" NOT NULL,
    status character varying(16) COLLATE pg_catalog."default" NOT NULL,
    remote_job_id character varying(255) COLLATE pg_catalog."default",
    result character varying(100000) COLLATE pg_catalog."default",
    error_code character varying(255) COLLATE pg_catalog."default",
    error_message character varying(1000) COLLATE pg_catalog."default",
    error_retryable boolean NOT NULL DEFAULT false,
    finished_at timestamp without time zone,
    owner_user_id bigint NOT NULL,
    CONSTRAINT ml_job_pkey PRIMARY KEY (id),

    -- Jobs belong to the person who started them and mean nothing without them, so deleting an
    -- account takes its jobs with it rather than leaving orphans the cleanup job would keep.
    CONSTRAINT ml_job_owner_fkey FOREIGN KEY (owner_user_id)
        REFERENCES public.cookpal_user (user_id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE CASCADE
);

-- The poller reads unfinished jobs on a short interval; this is the read it makes.
CREATE INDEX IF NOT EXISTS ml_job_status_idx ON public.ml_job (status);

-- Counting one user's jobs for today, on every submission.
CREATE INDEX IF NOT EXISTS ml_job_owner_created_idx ON public.ml_job (owner_user_id, created_on);

-- The retention sweep.
CREATE INDEX IF NOT EXISTS ml_job_finished_at_idx ON public.ml_job (finished_at);
