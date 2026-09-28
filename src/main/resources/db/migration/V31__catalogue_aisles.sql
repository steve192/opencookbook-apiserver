-- The catalogue now also says where a food is sold and which foods a shopping list offers as tiles.
-- Filled by the next dataset import; custom foods stay unplaced until an administrator places them.
ALTER TABLE public.catalogue_food ADD COLUMN aisle character varying(32);
ALTER TABLE public.catalogue_food ADD COLUMN shopping_tile boolean NOT NULL DEFAULT false;
ALTER TABLE public.catalogue_food ADD COLUMN icon character varying(64);

CREATE INDEX catalogue_food_shopping_tiles ON public.catalogue_food (id) WHERE shopping_tile;

-- The dataset is a food catalogue, not only nutrition data.
ALTER TABLE public.nutrition_dataset_import RENAME TO catalogue_dataset_import;
ALTER TABLE public.catalogue_dataset_import RENAME CONSTRAINT nutrition_dataset_import_pkey TO catalogue_dataset_import_pkey;
ALTER TABLE public.catalogue_dataset_import RENAME CONSTRAINT nutrition_dataset_import_known_status
    TO catalogue_dataset_import_known_status;
