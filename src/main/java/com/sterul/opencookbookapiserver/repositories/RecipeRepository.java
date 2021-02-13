package com.sterul.opencookbookapiserver.repositories;

import com.sterul.opencookbookapiserver.entities.Recipe;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RecipeRepository extends JpaRepository<Recipe, Long> {
 
}