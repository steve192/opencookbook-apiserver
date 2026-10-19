-- Water content, as both catalogue sources state it for every food. The bread tool counts it towards a dough's water.
ALTER TABLE public.catalogue_food
    ADD COLUMN water_g_per_100g real;

-- Feature facts: what one feature knows about some catalogue foods, filled by the dataset import and read by that
-- feature alone (docs/catalogue.md). Rows go with their food.

-- The seasonal calendar's fruit and vegetables, and how they keep at home.
CREATE TABLE public.seasonal_produce
(
    catalogue_food_id bigint NOT NULL,
    category character varying(16) NOT NULL,
    storage_place character varying(16),
    storage_cold_sensitive boolean NOT NULL DEFAULT false,
    storage_ethylene_producer boolean NOT NULL DEFAULT false,
    storage_ethylene_sensitive boolean NOT NULL DEFAULT false,
    CONSTRAINT seasonal_produce_pkey PRIMARY KEY (catalogue_food_id),
    CONSTRAINT seasonal_produce_food_fkey FOREIGN KEY (catalogue_food_id)
        REFERENCES public.catalogue_food (id) ON DELETE CASCADE
);

CREATE TABLE public.seasonal_produce_month
(
    catalogue_food_id bigint NOT NULL,
    region character varying(2) NOT NULL,
    month_of_year smallint NOT NULL,
    method character varying(32) NOT NULL,
    CONSTRAINT seasonal_produce_month_pkey PRIMARY KEY (catalogue_food_id, region, month_of_year, method),
    CONSTRAINT seasonal_produce_month_produce_fkey FOREIGN KEY (catalogue_food_id)
        REFERENCES public.seasonal_produce (catalogue_food_id) ON DELETE CASCADE,
    CONSTRAINT seasonal_produce_month_month_of_year CHECK (month_of_year BETWEEN 1 AND 12)
);

-- What seeds, flakes and grains soak up in a soaker, for the bread tool.
CREATE TABLE public.soaking_food
(
    catalogue_food_id bigint NOT NULL,
    absorption real NOT NULL,
    CONSTRAINT soaking_food_pkey PRIMARY KEY (catalogue_food_id),
    CONSTRAINT soaking_food_food_fkey FOREIGN KEY (catalogue_food_id)
        REFERENCES public.catalogue_food (id) ON DELETE CASCADE,
    CONSTRAINT soaking_food_absorption CHECK (absorption > 0)
);
