package com.sterul.opencookbookapiserver.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sterul.opencookbookapiserver.entities.recipe.RecipeClassificationRun;

public interface RecipeClassificationRunRepository extends JpaRepository<RecipeClassificationRun, Long> {

    List<RecipeClassificationRun> findAllByOrderByIdDesc();
}
