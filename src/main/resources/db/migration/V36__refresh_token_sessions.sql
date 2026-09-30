-- Refresh tokens are kept as SHA-256 hashes, replaced on use and grouped by sign in, so that one
-- presented twice can end that sign in everywhere. Tokens already handed out keep working: their
-- hash is what a client presenting them now matches.
CREATE SEQUENCE IF NOT EXISTS public.refresh_token_seq INCREMENT 1 START 1 MINVALUE 1 CACHE 1;

DELETE FROM public.refresh_token WHERE owner_user_id IS NULL OR valid_until IS NULL OR valid_until < now();

ALTER TABLE public.refresh_token
    ADD COLUMN id bigint,
    ADD COLUMN token_hash character varying(64),
    ADD COLUMN session_id character varying(36),
    ADD COLUMN password_at timestamp(6) with time zone,
    ADD COLUMN replaced_at timestamp(6) with time zone;

UPDATE public.refresh_token SET
    id = nextval('public.refresh_token_seq'),
    token_hash = encode(sha256(convert_to(token, 'UTF8')), 'hex'),
    session_id = gen_random_uuid()::text;

ALTER TABLE public.refresh_token DROP CONSTRAINT refresh_token_pkey;
ALTER TABLE public.refresh_token DROP COLUMN token;
ALTER TABLE public.refresh_token DROP CONSTRAINT IF EXISTS fknrh9b93jfgfblg73evvi5y11t;

ALTER TABLE public.refresh_token
    ALTER COLUMN id SET NOT NULL,
    ALTER COLUMN token_hash SET NOT NULL,
    ALTER COLUMN session_id SET NOT NULL,
    ALTER COLUMN owner_user_id SET NOT NULL,
    ALTER COLUMN valid_until SET NOT NULL,
    ADD CONSTRAINT refresh_token_pkey PRIMARY KEY (id),
    ADD CONSTRAINT refresh_token_token_hash_key UNIQUE (token_hash),
    ADD CONSTRAINT refresh_token_owner_fkey FOREIGN KEY (owner_user_id)
        REFERENCES public.cookpal_user (user_id) ON DELETE CASCADE;

CREATE INDEX refresh_token_by_owner ON public.refresh_token (owner_user_id);
CREATE INDEX refresh_token_by_session ON public.refresh_token (session_id);
CREATE INDEX refresh_token_by_valid_until ON public.refresh_token (valid_until);
