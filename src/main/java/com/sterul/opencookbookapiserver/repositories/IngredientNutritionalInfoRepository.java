package com.sterul.opencookbookapiserver.repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sterul.opencookbookapiserver.entities.IngredientNutritionalInfo;

public interface IngredientNutritionalInfoRepository extends JpaRepository<IngredientNutritionalInfo, Long>{
    
}
