-- Items flagged as urgent, shown first within their aisle.
ALTER TABLE public.shopping_item ADD COLUMN prioritized boolean NOT NULL DEFAULT false;
