package com.sterul.opencookbookapiserver.repositories;

import java.util.List;
import java.util.Optional;

import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.entities.recipe.RecipeGroup;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RecipeGroupRepository extends JpaRepository<RecipeGroup, Long> {
    List<RecipeGroup> findByOwner(CookpalUser owner);

    Optional<RecipeGroup> findByIdAndOwner(Long id, CookpalUser owner);
}
