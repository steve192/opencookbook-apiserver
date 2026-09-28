-- Shopping lists: a person's own or a household's, synced by version so a phone can work offline.

-- Null until the first shopping import asked where the list should go.
ALTER TABLE public.cookpal_user ADD COLUMN shopping_provider character varying(16);

ALTER TABLE public.bring_export ADD COLUMN title character varying(255);

CREATE SEQUENCE IF NOT EXISTS public.shopping_list_seq INCREMENT 1 START 1 MINVALUE 1 CACHE 1;
CREATE SEQUENCE IF NOT EXISTS public.shopping_staple_seq INCREMENT 1 START 1 MINVALUE 1 CACHE 1;

CREATE TABLE public.shopping_list
(
    id bigint NOT NULL,
    created_on timestamp(6) with time zone,
    last_change timestamp(6) with time zone,
    -- Null for an unrenamed default list; the app shows its own words for it.
    name character varying(64),
    owner_user_id bigint,
    household_id character varying(36),
    default_list boolean NOT NULL DEFAULT false,
    -- Raised by every item change; what a device syncs against.
    version bigint NOT NULL DEFAULT 0,
    -- Tombstones up to this version are gone, so a device behind it needs a full snapshot.
    purged_version bigint NOT NULL DEFAULT 0,
    CONSTRAINT shopping_list_pkey PRIMARY KEY (id),
    CONSTRAINT shopping_list_owner_fkey FOREIGN KEY (owner_user_id)
        REFERENCES public.cookpal_user (user_id) ON DELETE CASCADE,
    CONSTRAINT shopping_list_household_fkey FOREIGN KEY (household_id)
        REFERENCES public.household (id) ON DELETE CASCADE,
    CONSTRAINT shopping_list_one_scope CHECK ((owner_user_id IS NULL) <> (household_id IS NULL))
);

CREATE INDEX shopping_list_by_owner ON public.shopping_list (owner_user_id);
CREATE INDEX shopping_list_by_household ON public.shopping_list (household_id);
CREATE UNIQUE INDEX shopping_list_one_default_per_owner ON public.shopping_list (owner_user_id)
    WHERE default_list AND owner_user_id IS NOT NULL;
CREATE UNIQUE INDEX shopping_list_one_default_per_household ON public.shopping_list (household_id)
    WHERE default_list AND household_id IS NOT NULL;

-- The id is chosen by the device, so an item added offline can be ticked before it was ever synced.
CREATE TABLE public.shopping_item
(
    id character varying(36) NOT NULL,
    created_on timestamp(6) with time zone,
    last_change timestamp(6) with time zone,
    list_id bigint NOT NULL,
    name character varying(120) NOT NULL,
    name_key character varying(120) NOT NULL,
    spec character varying(200),
    aisle character varying(32) NOT NULL,
    aisle_manual boolean NOT NULL DEFAULT false,
    icon character varying(64),
    status character varying(16) NOT NULL,
    added_by_user_id bigint,
    bought_at timestamp(6) with time zone,
    version bigint NOT NULL,
    deleted boolean NOT NULL DEFAULT false,
    CONSTRAINT shopping_item_pkey PRIMARY KEY (id),
    CONSTRAINT shopping_item_list_fkey FOREIGN KEY (list_id)
        REFERENCES public.shopping_list (id) ON DELETE CASCADE,
    CONSTRAINT shopping_item_added_by_fkey FOREIGN KEY (added_by_user_id)
        REFERENCES public.cookpal_user (user_id) ON DELETE SET NULL
);

CREATE UNIQUE INDEX shopping_item_one_per_name ON public.shopping_item (list_id, name_key) WHERE NOT deleted;
CREATE INDEX shopping_item_by_version ON public.shopping_item (list_id, version);

-- Which meals an item was imported for.
CREATE TABLE public.shopping_item_source
(
    shopping_item_id character varying(36) NOT NULL,
    title character varying(255) NOT NULL,
    plan_date date,
    CONSTRAINT shopping_item_source_item_fkey FOREIGN KEY (shopping_item_id)
        REFERENCES public.shopping_item (id) ON DELETE CASCADE
);

CREATE INDEX shopping_item_source_by_item ON public.shopping_item_source (shopping_item_id);

-- Op ids already applied, so a batch retried after a lost response is not applied twice.
CREATE TABLE public.shopping_applied_op
(
    op_id character varying(36) NOT NULL,
    list_id bigint NOT NULL,
    applied_on timestamp(6) with time zone NOT NULL,
    CONSTRAINT shopping_applied_op_pkey PRIMARY KEY (op_id),
    CONSTRAINT shopping_applied_op_list_fkey FOREIGN KEY (list_id)
        REFERENCES public.shopping_list (id) ON DELETE CASCADE
);

CREATE INDEX shopping_applied_op_by_age ON public.shopping_applied_op (applied_on);

-- What a person keeps at home: learned from the lines they untick when importing.
CREATE TABLE public.shopping_staple
(
    id bigint NOT NULL,
    created_on timestamp(6) with time zone,
    last_change timestamp(6) with time zone,
    user_id bigint NOT NULL,
    name_key character varying(120) NOT NULL,
    name character varying(120) NOT NULL,
    unticked_streak integer NOT NULL DEFAULT 0,
    staple boolean NOT NULL DEFAULT false,
    CONSTRAINT shopping_staple_pkey PRIMARY KEY (id),
    CONSTRAINT shopping_staple_unique UNIQUE (user_id, name_key),
    CONSTRAINT shopping_staple_user_fkey FOREIGN KEY (user_id)
        REFERENCES public.cookpal_user (user_id) ON DELETE CASCADE
);
