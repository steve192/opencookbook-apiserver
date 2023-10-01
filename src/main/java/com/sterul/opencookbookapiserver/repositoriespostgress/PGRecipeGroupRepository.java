package com.sterul.opencookbookapiserver.repositoriespostgress;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sterul.opencookbookapiserver.repositoriespostgress.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.repositoriespostgress.entities.recipe.RecipeGroup;

public interface PGRecipeGroupRepository extends JpaRepository<RecipeGroup, Long> {
    List<RecipeGroup> findByOwner(CookpalUser owner);
}
