package com.sterul.opencookbookapiserver.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sterul.opencookbookapiserver.entities.catalogue.IngredientRelinkRun;

public interface IngredientRelinkRunRepository extends JpaRepository<IngredientRelinkRun, Long> {

    List<IngredientRelinkRun> findAllByOrderByIdDesc();
}
