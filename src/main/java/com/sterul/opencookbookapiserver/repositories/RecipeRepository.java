package com.sterul.opencookbookapiserver.repositories;

import java.util.List;

import com.sterul.opencookbookapiserver.entities.account.User;
import com.sterul.opencookbookapiserver.entities.recipe.Recipe;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RecipeRepository extends JpaRepository<Recipe, Long> {
 
    List<Recipe> findByTitleIgnoreCaseContaining(String searchString);
    List<Recipe> findByOwner(User owner);
}