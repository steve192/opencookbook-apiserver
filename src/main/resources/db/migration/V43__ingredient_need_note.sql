-- What a recipe line says beside its food ("weich", "fein gehackt"): shown with the line, never used to tell the food.
ALTER TABLE public.ingredient_need ADD COLUMN note character varying(500) NOT NULL DEFAULT '';

-- Replaced by the note on the line. It held the note of whichever recipe created the ingredient and was never shown.
ALTER TABLE public.ingredient DROP COLUMN additional_info;

-- No amount is null, as every source writes it now; old imports wrote 0 ("Salz").
UPDATE public.ingredient_need SET amount = NULL WHERE amount = 0;
