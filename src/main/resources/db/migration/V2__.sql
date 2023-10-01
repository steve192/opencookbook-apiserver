-- This script was generated by the Schema Diff utility in pgAdmin 4. 
-- For the circular dependencies, the order in which Schema Diff writes the objects is not very sophisticated 
-- and may require manual changes to the script to ensure changes are applied in the correct order.
-- Please report an issue for any failure with the reproduction steps. 
BEGIN; 

DO $$ 
DECLARE 
    max_id integer;
BEGIN 
    -- Finde den maximalen Wert aus dem Feld "id" in der Tabelle public.cookpal_user
    SELECT MAX(id) INTO max_id FROM public.ingredient;

    -- Setze den Startwert der Sequence auf den gefundenen maximalen Wert plus 1
    EXECUTE 'CREATE SEQUENCE IF NOT EXISTS public.ingredient_seq
              INCREMENT 50
              START ' || COALESCE(max_id + 1, 1) || -- Wenn die Tabelle leer ist, setze Startwert auf 1
              ' MINVALUE 1
              MAXVALUE 9223372036854775807
              CACHE 1';
END $$;

DO $$ 
DECLARE 
    max_id integer;
BEGIN 
    -- Finde den maximalen Wert aus dem Feld "id" in der Tabelle public.cookpal_user
    SELECT MAX(id) INTO max_id FROM public.recipe;

    -- Setze den Startwert der Sequence auf den gefundenen maximalen Wert plus 1
    EXECUTE 'CREATE SEQUENCE IF NOT EXISTS public.recipe_seq
              INCREMENT 50
              START ' || COALESCE(max_id + 1, 1) || -- Wenn die Tabelle leer ist, setze Startwert auf 1
              ' MINVALUE 1
              MAXVALUE 9223372036854775807
              CACHE 1';
END $$;

DO $$ 
DECLARE 
    max_id integer;
BEGIN 
    EXECUTE 'CREATE SEQUENCE IF NOT EXISTS public.hibernate_sequence
              INCREMENT 50
              START 100000'
              ' MINVALUE 1
              MAXVALUE 9223372036854775807
              CACHE 1';
END $$;


DO $$ 
DECLARE 
    max_id integer;
BEGIN 
    -- Finde den maximalen Wert aus dem Feld "id" in der Tabelle public.cookpal_user
    SELECT MAX(id) INTO max_id FROM public.ingredient_need;

    -- Setze den Startwert der Sequence auf den gefundenen maximalen Wert plus 1
    EXECUTE 'CREATE SEQUENCE IF NOT EXISTS public.ingredient_need_seq
              INCREMENT 50
              START ' || COALESCE(max_id + 1, 1) || -- Wenn die Tabelle leer ist, setze Startwert auf 1
              ' MINVALUE 1
              MAXVALUE 9223372036854775807
              CACHE 1';
END $$;

DO $$ 
DECLARE 
    max_id integer;
BEGIN 
    -- Finde den maximalen Wert aus dem Feld "id" in der Tabelle public.cookpal_user
    SELECT MAX(id) INTO max_id FROM public.weekplan_day;

    -- Setze den Startwert der Sequence auf den gefundenen maximalen Wert plus 1
    EXECUTE 'CREATE SEQUENCE IF NOT EXISTS public.weekplan_day_seq
              INCREMENT 50
              START ' || COALESCE(max_id + 1, 1) || -- Wenn die Tabelle leer ist, setze Startwert auf 1
              ' MINVALUE 1
              MAXVALUE 9223372036854775807
              CACHE 1';
END $$;

DO $$ 
DECLARE 
    max_id integer;
BEGIN 
    -- Finde den maximalen Wert aus dem Feld "id" in der Tabelle public.cookpal_user
    SELECT MAX(id) INTO max_id FROM public.recipe_group;

    -- Setze den Startwert der Sequence auf den gefundenen maximalen Wert plus 1
    EXECUTE 'CREATE SEQUENCE IF NOT EXISTS public.recipe_group_seq
              INCREMENT 50
              START ' || COALESCE(max_id + 1, 1) || -- Wenn die Tabelle leer ist, setze Startwert auf 1
              ' MINVALUE 1
              MAXVALUE 9223372036854775807
              CACHE 1';
END $$;

DO $$ 
DECLARE 
    max_id integer;
BEGIN 
    -- Finde den maximalen Wert aus dem Feld "id" in der Tabelle public.cookpal_user
    SELECT MAX(user_id) INTO max_id FROM public.cookpal_user;

    -- Setze den Startwert der Sequence auf den gefundenen maximalen Wert plus 1
    EXECUTE 'CREATE SEQUENCE IF NOT EXISTS public.cookpal_user_seq
              INCREMENT 50
              START ' || COALESCE(max_id + 1, 1) || -- Wenn die Tabelle leer ist, setze Startwert auf 1
              ' MINVALUE 1
              MAXVALUE 9223372036854775807
              CACHE 1';
END $$;


END;