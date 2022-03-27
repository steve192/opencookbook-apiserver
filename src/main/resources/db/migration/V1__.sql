CREATE SEQUENCE hibernate_sequence START WITH 1 INCREMENT BY 1;

CREATE TABLE activation_link (
  id VARCHAR(255) NOT NULL,
   user_user_id BIGINT,
   CONSTRAINT pk_activationlink PRIMARY KEY (id)
);

CREATE TABLE ingredient (
  id BIGINT NOT NULL,
   name VARCHAR(255),
   CONSTRAINT pk_ingredient PRIMARY KEY (id)
);

CREATE TABLE ingredient_need (
  id BIGINT NOT NULL,
   ingredient_id BIGINT,
   amount FLOAT,
   unit VARCHAR(255),
   CONSTRAINT pk_ingredientneed PRIMARY KEY (id)
);

CREATE TABLE password_reset_link (
  id VARCHAR(255) NOT NULL,
   valid_until TIMESTAMP,
   user_user_id BIGINT,
   CONSTRAINT pk_passwordresetlink PRIMARY KEY (id)
);

CREATE TABLE recipe (
  id BIGINT NOT NULL,
   title VARCHAR(255),
   owner_user_id BIGINT,
   servings INT,
   preparation_time BIGINT,
   total_time BIGINT,
   recipe_type INT,
   CONSTRAINT pk_recipe PRIMARY KEY (id)
);

CREATE TABLE recipe_group (
  id BIGINT NOT NULL,
   title VARCHAR(255),
   owner_user_id BIGINT,
   CONSTRAINT pk_recipegroup PRIMARY KEY (id)
);

CREATE TABLE recipe_image (
  uuid VARCHAR(255) NOT NULL,
   owner_user_id BIGINT,
   CONSTRAINT pk_recipeimage PRIMARY KEY (uuid)
);

CREATE TABLE recipe_images (
  recipe_id BIGINT NOT NULL,
   images_uuid VARCHAR(255) NOT NULL
);

CREATE TABLE recipe_needed_ingredients (
  recipe_id BIGINT NOT NULL,
   needed_ingredients_id BIGINT NOT NULL
);

CREATE TABLE recipe_preparation_steps (
  recipe_id BIGINT NOT NULL,
   preparation_steps VARCHAR(10000)
);

CREATE TABLE recipe_recipe_groups (
  recipe_id BIGINT NOT NULL,
   recipe_groups_id BIGINT NOT NULL
);

CREATE TABLE refresh_token (
  token VARCHAR(255) NOT NULL,
   valid_until TIMESTAMP,
   owner_user_id BIGINT,
   CONSTRAINT pk_refreshtoken PRIMARY KEY (token)
);

CREATE TABLE "USER" (
  user_id BIGINT NOT NULL,
   email_address VARCHAR(255),
   password_hash VARCHAR(255),
   activated BOOLEAN DEFAULT FALSE,
   CONSTRAINT pk_user PRIMARY KEY (user_id)
);

CREATE TABLE weekplan_day (
  id BIGINT NOT NULL,
   "day" date,
   owner_user_id BIGINT,
   CONSTRAINT pk_weekplanday PRIMARY KEY (id)
);

CREATE TABLE weekplan_day_recipe (
  id VARCHAR(255) NOT NULL,
   recipe_id BIGINT,
   is_simple_recipe BOOLEAN,
   simple_recipe_text VARCHAR(255),
   CONSTRAINT pk_weekplandayrecipe PRIMARY KEY (id)
);

CREATE TABLE weekplan_day_recipes (
  weekplan_day_id BIGINT NOT NULL,
   recipes_id VARCHAR(255) NOT NULL
);

ALTER TABLE recipe_images ADD CONSTRAINT uc_recipe_images_images_uuid UNIQUE (images_uuid);

ALTER TABLE recipe_needed_ingredients ADD CONSTRAINT uc_recipe_needed_ingredients_neededingredients UNIQUE (needed_ingredients_id);

ALTER TABLE weekplan_day_recipes ADD CONSTRAINT uc_weekplan_day_recipes_recipes UNIQUE (recipes_id);

ALTER TABLE activation_link ADD CONSTRAINT FK_ACTIVATIONLINK_ON_USER_USERID FOREIGN KEY (user_user_id) REFERENCES "USER" (user_id);

ALTER TABLE ingredient_need ADD CONSTRAINT FK_INGREDIENTNEED_ON_INGREDIENT FOREIGN KEY (ingredient_id) REFERENCES ingredient (id);

ALTER TABLE password_reset_link ADD CONSTRAINT FK_PASSWORDRESETLINK_ON_USER_USERID FOREIGN KEY (user_user_id) REFERENCES "USER" (user_id);

ALTER TABLE recipe_group ADD CONSTRAINT FK_RECIPEGROUP_ON_OWNER_USERID FOREIGN KEY (owner_user_id) REFERENCES "USER" (user_id);

ALTER TABLE recipe_image ADD CONSTRAINT FK_RECIPEIMAGE_ON_OWNER_USERID FOREIGN KEY (owner_user_id) REFERENCES "USER" (user_id);

ALTER TABLE recipe ADD CONSTRAINT FK_RECIPE_ON_OWNER_USERID FOREIGN KEY (owner_user_id) REFERENCES "USER" (user_id);

ALTER TABLE refresh_token ADD CONSTRAINT FK_REFRESHTOKEN_ON_OWNER_USERID FOREIGN KEY (owner_user_id) REFERENCES "USER" (user_id);

ALTER TABLE weekplan_day_recipe ADD CONSTRAINT FK_WEEKPLANDAYRECIPE_ON_RECIPE FOREIGN KEY (recipe_id) REFERENCES recipe (id);

ALTER TABLE weekplan_day ADD CONSTRAINT FK_WEEKPLANDAY_ON_OWNER_USERID FOREIGN KEY (owner_user_id) REFERENCES "USER" (user_id);

ALTER TABLE recipe_images ADD CONSTRAINT fk_recima_on_recipe FOREIGN KEY (recipe_id) REFERENCES recipe (id);

ALTER TABLE recipe_images ADD CONSTRAINT fk_recima_on_recipe_image FOREIGN KEY (images_uuid) REFERENCES recipe_image (uuid);

ALTER TABLE recipe_preparation_steps ADD CONSTRAINT fk_recipe_preparationsteps_on_recipe FOREIGN KEY (recipe_id) REFERENCES recipe (id);

ALTER TABLE recipe_needed_ingredients ADD CONSTRAINT fk_recneeing_on_ingredient_need FOREIGN KEY (needed_ingredients_id) REFERENCES ingredient_need (id);

ALTER TABLE recipe_needed_ingredients ADD CONSTRAINT fk_recneeing_on_recipe FOREIGN KEY (recipe_id) REFERENCES recipe (id);

ALTER TABLE recipe_recipe_groups ADD CONSTRAINT fk_recrecgro_on_recipe FOREIGN KEY (recipe_id) REFERENCES recipe (id);

ALTER TABLE recipe_recipe_groups ADD CONSTRAINT fk_recrecgro_on_recipe_group FOREIGN KEY (recipe_groups_id) REFERENCES recipe_group (id);

ALTER TABLE weekplan_day_recipes ADD CONSTRAINT fk_weedayrec_on_weekplan_day FOREIGN KEY (weekplan_day_id) REFERENCES weekplan_day (id);

ALTER TABLE weekplan_day_recipes ADD CONSTRAINT fk_weedayrec_on_weekplan_day_recipe FOREIGN KEY (recipes_id) REFERENCES weekplan_day_recipe (id);