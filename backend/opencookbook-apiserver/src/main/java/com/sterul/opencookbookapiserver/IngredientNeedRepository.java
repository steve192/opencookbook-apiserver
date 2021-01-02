package com.sterul.opencookbookapiserver;

import com.sterul.opencookbookapiserver.entities.IngredientNeed;

import org.springframework.data.jpa.repository.JpaRepository;

public interface IngredientNeedRepository extends JpaRepository<IngredientNeed, Long> {
    
}
