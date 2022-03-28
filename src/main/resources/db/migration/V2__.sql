alter table activation_link add created_on TIMESTAMP;
update activation_link set created_on = CURRENT_TIMESTAMP;

alter table activation_link add last_change TIMESTAMP;
update activation_link set last_change = CURRENT_TIMESTAMP;

-- Cleanup orphans
delete from ingredient where not exists (SELECT * FROM ingredient_need where ingredient_need.ingredient_id = ingredient.id);

alter table ingredient add created_on TIMESTAMP;
update ingredient set created_on = CURRENT_TIMESTAMP;

alter table ingredient add is_public_ingredient BOOLEAN;
update ingredient set is_public_ingredient = false;


alter table ingredient add last_change TIMESTAMP;
update ingredient set last_change = CURRENT_TIMESTAMP;

alter table ingredient add owner_user_id BIGINT;

alter table ingredient_need add created_on TIMESTAMP;
update ingredient_need set created_on = CURRENT_TIMESTAMP;

alter table ingredient_need add last_change TIMESTAMP;
update ingredient_need set last_change = CURRENT_TIMESTAMP;

alter table password_reset_link add created_on TIMESTAMP;
update password_reset_link set created_on = CURRENT_TIMESTAMP;

alter table password_reset_link add last_change TIMESTAMP;
update password_reset_link set last_change = CURRENT_TIMESTAMP;

alter table recipe add created_on TIMESTAMP;
update recipe set created_on = CURRENT_TIMESTAMP;

alter table recipe add last_change TIMESTAMP;
update recipe set last_change = CURRENT_TIMESTAMP;

alter table recipe_group add created_on TIMESTAMP;
update recipe_group set created_on = CURRENT_TIMESTAMP;

alter table recipe_group add last_change TIMESTAMP;
update recipe_group set last_change = CURRENT_TIMESTAMP;

alter table recipe_image add created_on TIMESTAMP;
update recipe_image set created_on = CURRENT_TIMESTAMP;

alter table recipe_image add last_change TIMESTAMP;
update recipe_image set last_change = CURRENT_TIMESTAMP;

alter table refresh_token add created_on TIMESTAMP;
update refresh_token set created_on = CURRENT_TIMESTAMP;

alter table refresh_token add last_change TIMESTAMP;
update refresh_token set last_change = CURRENT_TIMESTAMP;

alter table "USER" add created_on TIMESTAMP;
update "USER" set created_on = CURRENT_TIMESTAMP;

alter table "USER" add last_change TIMESTAMP;
update "USER" set last_change = CURRENT_TIMESTAMP;

alter table weekplan_day add created_on TIMESTAMP;
update weekplan_day set created_on = CURRENT_TIMESTAMP;

alter table weekplan_day add last_change TIMESTAMP;
update weekplan_day set last_change = CURRENT_TIMESTAMP;

alter table ingredient add CONSTRAINT FK_INGREDIENT_ON_OWNER_USERID FOREIGN KEY (owner_user_id) REFERENCES "USER" (user_id);

alter table weekplan_day_recipe alter COLUMN is_simple_recipe SET NULL;

alter table recipe alter COLUMN servings SET NULL;




-- Update ingredient owner from linked recipes
UPDATE ingredient a
set a.owner_user_id = (
SELECT recipe.owner_user_id FROM ingredient
        join ingredient_need on ingredient.id = ingredient_need.ingredient_id
        join recipe_needed_ingredients on recipe_needed_ingredients.needed_ingredients_id = ingredient_need.id
        join recipe on recipe_needed_ingredients.recipe_id = recipe.id
        where ingredient.id = a.id
        limit 1
)
where a.is_public_ingredient=false and a.owner_user_id is null;