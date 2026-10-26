-- Shopping suggestions: lists remember what was on them and learn what they buy at a rhythm.

-- Whether the app shows suggestions of regularly bought items; lists learn them regardless.
ALTER TABLE public.cookpal_user ADD COLUMN shopping_suggestions boolean NOT NULL DEFAULT true;

-- When an item last went onto its list from not being on it; asking for more of it does not move this.
ALTER TABLE public.shopping_item ADD COLUMN listed_at timestamp(6) with time zone NOT NULL DEFAULT now();
UPDATE public.shopping_item SET listed_at = added_at;

CREATE SEQUENCE IF NOT EXISTS public.shopping_list_event_seq INCREMENT 1 START 1 MINVALUE 1 CACHE 1;
CREATE SEQUENCE IF NOT EXISTS public.shopping_regular_seq INCREMENT 1 START 1 MINVALUE 1 CACHE 1;

-- One change a person made to an item of a list: the list's history, and what it learns purchases and earlier
-- entries from. An item taken off within two minutes leaves none. Kept for a year.
CREATE TABLE public.shopping_list_event
(
    id bigint NOT NULL,
    list_id bigint NOT NULL,
    -- No reference: items are purged once deleted, their history is kept.
    item_id character varying(36) NOT NULL,
    -- By the clock of the device that made the change.
    at timestamp(6) with time zone NOT NULL,
    kind character varying(16) NOT NULL,
    name_key character varying(120) NOT NULL,
    name character varying(120) NOT NULL,
    aisle character varying(32) NOT NULL,
    icon character varying(64),
    spec character varying(200),
    previous_name character varying(120),
    actor_user_id bigint,
    CONSTRAINT shopping_list_event_pkey PRIMARY KEY (id),
    CONSTRAINT shopping_list_event_list_fkey FOREIGN KEY (list_id)
        REFERENCES public.shopping_list (id) ON DELETE CASCADE,
    CONSTRAINT shopping_list_event_actor_fkey FOREIGN KEY (actor_user_id)
        REFERENCES public.cookpal_user (user_id) ON DELETE SET NULL
);

CREATE INDEX shopping_list_event_by_time ON public.shopping_list_event (list_id, at);
CREATE INDEX shopping_list_event_by_name ON public.shopping_list_event (list_id, name_key, kind, at);
CREATE INDEX shopping_list_event_by_age ON public.shopping_list_event (at);

-- The meals an addition was imported for.
CREATE TABLE public.shopping_list_event_source
(
    shopping_list_event_id bigint NOT NULL,
    title character varying(255) NOT NULL,
    plan_date date,
    CONSTRAINT shopping_list_event_source_event_fkey FOREIGN KEY (shopping_list_event_id)
        REFERENCES public.shopping_list_event (id) ON DELETE CASCADE
);

CREATE INDEX shopping_list_event_source_by_event ON public.shopping_list_event_source (shopping_list_event_id);

-- What a list learned from buying one name: its rhythm, and when its suggestion is first shown.
CREATE TABLE public.shopping_regular
(
    id bigint NOT NULL,
    list_id bigint NOT NULL,
    name_key character varying(120) NOT NULL,
    name character varying(120) NOT NULL,
    aisle character varying(32) NOT NULL,
    icon character varying(64),
    -- Null while the purchases make no rhythm.
    rhythm_seconds bigint,
    last_bought_at timestamp(6) with time zone,
    -- When the suggestion is first shown; "not now" moves it. Null while muted or without a rhythm.
    suggest_at timestamp(6) with time zone,
    muted boolean NOT NULL DEFAULT false,
    CONSTRAINT shopping_regular_pkey PRIMARY KEY (id),
    CONSTRAINT shopping_regular_list_fkey FOREIGN KEY (list_id)
        REFERENCES public.shopping_list (id) ON DELETE CASCADE,
    CONSTRAINT shopping_regular_one_per_name UNIQUE (list_id, name_key)
);

-- Shopping tiles become a feature fact (docs/catalogue.md): the foods a list offers to tap, with the word each shows.
-- The next dataset import fills them.
ALTER TABLE public.catalogue_food DROP COLUMN shopping_tile;

CREATE TABLE public.shopping_tile
(
    catalogue_food_id bigint NOT NULL,
    CONSTRAINT shopping_tile_pkey PRIMARY KEY (catalogue_food_id),
    CONSTRAINT shopping_tile_food_fkey FOREIGN KEY (catalogue_food_id)
        REFERENCES public.catalogue_food (id) ON DELETE CASCADE
);

CREATE TABLE public.shopping_tile_name
(
    catalogue_food_id bigint NOT NULL,
    language_iso_code character varying(8) NOT NULL,
    name character varying(255) NOT NULL,
    CONSTRAINT shopping_tile_name_pkey PRIMARY KEY (catalogue_food_id, language_iso_code),
    CONSTRAINT shopping_tile_name_tile_fkey FOREIGN KEY (catalogue_food_id)
        REFERENCES public.shopping_tile (catalogue_food_id) ON DELETE CASCADE
);
