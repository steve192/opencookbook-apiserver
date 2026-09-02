-- Recipe sharing: a link, addressed by an unguessable id, that anybody may follow to read one
-- recipe until it lapses.

CREATE TABLE IF NOT EXISTS public.share
(
    id character varying(255) COLLATE pg_catalog."default" NOT NULL,
    created_on timestamp without time zone,
    last_change timestamp without time zone,
    resource_type character varying(255) COLLATE pg_catalog."default" NOT NULL,
    visibility character varying(255) COLLATE pg_catalog."default" NOT NULL,
    expires_at timestamp without time zone NOT NULL,
    access_count bigint NOT NULL,
    owner_user_id bigint NOT NULL,
    recipe_id bigint,
    CONSTRAINT share_pkey PRIMARY KEY (id),

    -- Unlike the rest of the schema these cascade. A share that outlives the recipe it points at
    -- resolves to nothing, which looks to whoever was sent the link like the app is broken rather
    -- than like the recipe is gone. The application deletes them too; this is the half that also
    -- holds when a row is removed by hand.
    CONSTRAINT share_owner_fkey FOREIGN KEY (owner_user_id)
        REFERENCES public.cookpal_user (user_id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE CASCADE,
    CONSTRAINT share_recipe_fkey FOREIGN KEY (recipe_id)
        REFERENCES public.recipe (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE CASCADE,

    -- The typed foreign key has to match the discriminator. A polymorphic resource id column
    -- would have been shorter to write and would have allowed neither of these guarantees.
    CONSTRAINT share_resource_matches_type CHECK (
        resource_type = 'RECIPE' AND recipe_id IS NOT NULL
    ),
    CONSTRAINT share_known_visibility CHECK (
        visibility = 'PUBLIC_LINK'
    )
)

TABLESPACE pg_default;

-- "Share this recipe" must hand out the same link every time it is asked. Enforced here rather
-- than only in the service, because the service decides by reading and then writing: two requests
-- arriving together - a double tap on the share button - would otherwise both find no share and
-- both create one, leaving a second public link that its owner never sees and cannot revoke.
--
-- Restricted to public links so that a share aimed at a particular person can be added later
-- without colliding with the link.
CREATE UNIQUE INDEX IF NOT EXISTS share_one_public_link_per_recipe
    ON public.share (recipe_id)
    WHERE visibility = 'PUBLIC_LINK' AND recipe_id IS NOT NULL;

-- The deletion job asks for everything already lapsed.
CREATE INDEX IF NOT EXISTS share_expires_at
    ON public.share (expires_at);
