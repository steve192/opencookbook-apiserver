package com.sterul.opencookbookapiserver.repositories;

import java.util.List;

import com.sterul.opencookbookapiserver.entities.Recipe;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RecipeRepository extends JpaRepository<Recipe, Long> {
 
    List<Recipe> findByTitleIgnoreCaseContaining(String searchString);
}