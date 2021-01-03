package com.sterul.opencookbookapiserver.repositories;

import com.sterul.opencookbookapiserver.entities.Ingredient;

import org.springframework.data.jpa.repository.JpaRepository;

public interface IngredientRepository extends JpaRepository<Ingredient, Long>{
    
}
