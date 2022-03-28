alter table activation_link add created_on TIMESTAMP;

alter table activation_link add last_change TIMESTAMP;

alter table ingredient add created_on TIMESTAMP;

alter table ingredient add is_public_ingredient BOOLEAN;

alter table ingredient add last_change TIMESTAMP;

alter table ingredient add owner_user_id BIGINT;

alter table ingredient_need add created_on TIMESTAMP;

alter table ingredient_need add last_change TIMESTAMP;

alter table password_reset_link add created_on TIMESTAMP;

alter table password_reset_link add last_change TIMESTAMP;

alter table recipe add created_on TIMESTAMP;

alter table recipe add last_change TIMESTAMP;

alter table recipe_group add created_on TIMESTAMP;

alter table recipe_group add last_change TIMESTAMP;

alter table recipe_image add created_on TIMESTAMP;

alter table recipe_image add last_change TIMESTAMP;

alter table refresh_token add created_on TIMESTAMP;

alter table refresh_token add last_change TIMESTAMP;

alter table "USER" add created_on TIMESTAMP;

alter table "USER" add last_change TIMESTAMP;

alter table weekplan_day add created_on TIMESTAMP;

alter table weekplan_day add last_change TIMESTAMP;

alter table ingredient add CONSTRAINT FK_INGREDIENT_ON_OWNER_USERID FOREIGN KEY (owner_user_id) REFERENCES "USER" (user_id);

alter table weekplan_day_recipe alter COLUMN is_simple_recipe SET NULL;

alter table recipe alter COLUMN servings SET NULL;