-- Api keys: long-lived, scoped secrets for headless clients such as Home Assistant. Only a hash is kept.
CREATE SEQUENCE IF NOT EXISTS public.api_key_seq INCREMENT 1 START 1 MINVALUE 1 CACHE 1;

CREATE TABLE public.api_key
(
    id bigint NOT NULL,
    created_on timestamp(6) with time zone,
    last_change timestamp(6) with time zone,
    owner_user_id bigint NOT NULL,
    name character varying(60) NOT NULL,
    secret_hash character varying(64) NOT NULL,
    display_prefix character varying(16) NOT NULL,
    last_used_at timestamp(6) with time zone,
    CONSTRAINT api_key_pkey PRIMARY KEY (id),
    CONSTRAINT api_key_secret_hash_key UNIQUE (secret_hash),
    CONSTRAINT api_key_owner_fkey FOREIGN KEY (owner_user_id)
        REFERENCES public.cookpal_user (user_id) ON DELETE CASCADE
);

CREATE INDEX api_key_by_owner ON public.api_key (owner_user_id);

CREATE TABLE public.api_key_scope
(
    api_key_id bigint NOT NULL,
    scope character varying(32) NOT NULL,
    CONSTRAINT api_key_scope_pkey PRIMARY KEY (api_key_id, scope),
    CONSTRAINT api_key_scope_key_fkey FOREIGN KEY (api_key_id)
        REFERENCES public.api_key (id) ON DELETE CASCADE
);
