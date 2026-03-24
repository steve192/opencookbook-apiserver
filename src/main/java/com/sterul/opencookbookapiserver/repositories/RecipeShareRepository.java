package com.sterul.opencookbookapiserver.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sterul.opencookbookapiserver.entities.RecipeShare;
import com.sterul.opencookbookapiserver.entities.recipe.Recipe;

public interface RecipeShareRepository extends JpaRepository<RecipeShare, String>  {

    List<RecipeShare> findByRecipe(Recipe recipe);

    void deleteByRecipe(Recipe recipe);
    
}
