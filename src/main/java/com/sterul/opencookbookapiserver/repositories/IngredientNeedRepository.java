package com.sterul.opencookbookapiserver.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.sterul.opencookbookapiserver.entities.IngredientNeed;

public interface IngredientNeedRepository extends JpaRepository<IngredientNeed, Long> {

    /** With ingredient, owner and food: the lines a relink run may read again. */
    @Query("select need from IngredientNeed need join fetch need.ingredient ingredient join fetch ingredient.owner "
            + "left join fetch ingredient.catalogueFood where ingredient.linkSource is null or "
            + IngredientRepository.LINKED_AUTOMATICALLY)
    List<IngredientNeed> findAllOfIngredientsNoPersonDecided();
}
