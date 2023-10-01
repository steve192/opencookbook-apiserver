package com.sterul.opencookbookapiserver.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sterul.opencookbookapiserver.repositories.entities.account.User;
import com.sterul.opencookbookapiserver.repositories.entities.recipe.RecipeGroup;

public interface RecipeGroupRepository extends JpaRepository<RecipeGroup, Long> {
    List<RecipeGroup> findByOwner(User owner);
}
