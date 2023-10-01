package com.sterul.opencookbookapiserver.repositoriespostgress;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sterul.opencookbookapiserver.repositoriespostgress.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.repositoriespostgress.entities.recipe.Recipe;
import com.sterul.opencookbookapiserver.repositoriespostgress.entities.recipe.RecipeGroup;

public interface PGRecipeRepository extends JpaRepository<Recipe, Long> {

    List<Recipe> findByTitleIgnoreCaseContaining(String searchString);

    List<Recipe> findByOwner(CookpalUser owner);

    List<Recipe> findByOwnerAndRecipeTypeIn(CookpalUser owner, List<Recipe.RecipeType> recipeType);

    List<Recipe> findByRecipeGroups(RecipeGroup recipeGroup);
}