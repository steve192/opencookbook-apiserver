package com.sterul.opencookbookapiserver;

import com.sterul.opencookbookapiserver.entities.Ingredient;

import org.springframework.data.jpa.repository.JpaRepository;

public interface IngredientRepository extends JpaRepository<Ingredient, Long>{
    
}
