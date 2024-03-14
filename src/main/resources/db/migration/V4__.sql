CREATE TABLE IF NOT EXISTS public.bring_export
(
    base_amount integer NOT NULL,
    created_on timestamp(6) with time zone,
    last_change timestamp(6) with time zone,
    owner_user_id bigint,
    id character varying(255) COLLATE pg_catalog."default" NOT NULL,
    CONSTRAINT bring_export_pkey PRIMARY KEY (id),
    CONSTRAINT fktmegg7l89xndp3v5uhebhneo1 FOREIGN KEY (owner_user_id)
        REFERENCES public.cookpal_user (user_id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION
);



CREATE TABLE IF NOT EXISTS public.bring_export_ingredients
(
    ingredients character varying(10000) COLLATE pg_catalog."default",
    bring_export_id character varying(255) COLLATE pg_catalog."default" NOT NULL,
    CONSTRAINT fkgasff0g9qdn364vrw9nj105t4 FOREIGN KEY (bring_export_id)
        REFERENCES public.bring_export (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION
);

