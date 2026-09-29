-- When an item was last put on its list, so the app can show the newest last while adding.
ALTER TABLE public.shopping_item ADD COLUMN added_at timestamp(6) with time zone;
UPDATE public.shopping_item SET added_at = COALESCE(created_on, now());
ALTER TABLE public.shopping_item ALTER COLUMN added_at SET DEFAULT now();
ALTER TABLE public.shopping_item ALTER COLUMN added_at SET NOT NULL;
