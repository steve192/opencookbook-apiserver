package com.sterul.opencookbookapiserver.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.sterul.opencookbookapiserver.entities.nutrition.RecipeNutritionSummary;

public interface RecipeNutritionSummaryRepository extends JpaRepository<RecipeNutritionSummary, Long> {

    /** Everything the catalogue could have changed: cheaper to drop than to work out what moved. */
    @Modifying
    @Query("delete from RecipeNutritionSummary summary")
    int deleteAllSummaries();
}
