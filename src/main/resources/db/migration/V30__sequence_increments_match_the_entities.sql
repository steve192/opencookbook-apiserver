-- Every @SequenceGenerator in this codebase declares allocationSize = 1, and every sequence
-- added by a hand written migration since uses INCREMENT BY 1. These six are the oldest
-- tables, from when the schema came from Hibernate's auto DDL, which defaults allocationSize
-- to 50 and creates the sequence to match. Nobody chose 50 here, and nothing relies on it.
--
-- 50 is worth having when it is deliberate: Hibernate's pooled optimizer takes one nextval
-- and hands out 50 ids from memory, one round trip per 50 inserts. That needs the sequence
-- and allocationSize to agree. They do not agree here, so none of that happens: with
-- allocationSize = 1 Hibernate uses each nextval as an id directly, and the only effect of
-- the 50 is that ids jump in steps of 50. Schema validation refuses to start on the
-- mismatch, which is how it was found.
--
-- Lowering the increment cannot collide: it does not move last_value, and every one of these
-- sequences already sits at exactly max(id), so the next id is still one past the last used.

ALTER SEQUENCE public.cookpal_user_seq INCREMENT BY 1;
ALTER SEQUENCE public.recipe_seq INCREMENT BY 1;
ALTER SEQUENCE public.recipe_group_seq INCREMENT BY 1;
ALTER SEQUENCE public.ingredient_seq INCREMENT BY 1;
ALTER SEQUENCE public.ingredient_need_seq INCREMENT BY 1;
ALTER SEQUENCE public.weekplan_day_seq INCREMENT BY 1;
