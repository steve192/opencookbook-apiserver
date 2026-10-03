-- What an administrator chose for the whole instance: exactly one row. The setup locks it
-- before counting administrators, so two first visitors cannot both become one.
CREATE TABLE public.instance_settings
(
    id bigint NOT NULL,
    signup_mode character varying(32) NOT NULL DEFAULT 'OPEN',
    CONSTRAINT instance_settings_pkey PRIMARY KEY (id),
    CONSTRAINT instance_settings_single_row CHECK (id = 1)
);

INSERT INTO public.instance_settings (id, signup_mode) VALUES (1, 'OPEN');

-- Single-use links to create an active account. Never tied to an address.
CREATE TABLE public.invitation
(
    id character varying(43) NOT NULL,
    created_on timestamp(6) with time zone,
    last_change timestamp(6) with time zone,
    -- Null once the administrator who created it is deleted; the invitation outlives them.
    created_by_user_id bigint,
    expires_at timestamp(6) with time zone NOT NULL,
    CONSTRAINT invitation_pkey PRIMARY KEY (id),
    CONSTRAINT invitation_creator_fkey FOREIGN KEY (created_by_user_id)
        REFERENCES public.cookpal_user (user_id) ON DELETE SET NULL
);

CREATE INDEX invitation_by_expiry ON public.invitation (expires_at);
