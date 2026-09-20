-- Whether a catalogue food is animal, on the same three-level scale a recipe carries, so a recipe's
-- diet can be derived as the strictest of its ingredients'. Filled by the next dataset import;
-- an administrator's correction is marked ADMIN and survives later releases.

ALTER TABLE public.catalogue_food ADD COLUMN diet_class character varying(16);
ALTER TABLE public.catalogue_food ADD COLUMN diet_class_origin character varying(16);

ALTER TABLE public.catalogue_food ADD CONSTRAINT catalogue_food_diet_class_known
    CHECK (diet_class IS NULL OR diet_class IN ('VEGAN', 'VEGETARIAN', 'MEAT'));
ALTER TABLE public.catalogue_food ADD CONSTRAINT catalogue_food_diet_class_origin_known
    CHECK (diet_class_origin IS NULL OR diet_class_origin IN ('DATASET', 'ADMIN'));
-- A class without an origin would be a decision nobody can attribute, and an origin without a
-- class would claim somebody decided nothing.
ALTER TABLE public.catalogue_food ADD CONSTRAINT catalogue_food_diet_class_attributed
    CHECK ((diet_class IS NULL) = (diet_class_origin IS NULL));
