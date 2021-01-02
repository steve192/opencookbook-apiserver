package com.sterul.opencookbookapiserver;

import com.sterul.opencookbookapiserver.entities.Recipe;

import org.springframework.data.jpa.repository.JpaRepository;

interface RecipeRepository extends JpaRepository<Recipe, Long> {

}