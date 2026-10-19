package com.sterul.opencookbookapiserver.repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sterul.opencookbookapiserver.entities.baking.SoakingFood;

public interface SoakingFoodRepository extends JpaRepository<SoakingFood, Long> {
}
