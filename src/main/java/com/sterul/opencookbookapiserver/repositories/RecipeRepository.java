package com.sterul.opencookbookapiserver.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sterul.opencookbookapiserver.repositories.entities.account.User;
import com.sterul.opencookbookapiserver.repositories.entities.recipe.Recipe;
import com.sterul.opencookbookapiserver.repositories.entities.recipe.RecipeGroup;

public interface RecipeRepository extends JpaRepository<Recipe, Long> {

    List<Recipe> findByTitleIgnoreCaseContaining(String searchString);

    List<Recipe> findByOwner(User owner);

    List<Recipe> findByOwnerAndRecipeTypeIn(User owner, List<Recipe.RecipeType> recipeType);

    List<Recipe> findByRecipeGroups(RecipeGroup recipeGroup);
}