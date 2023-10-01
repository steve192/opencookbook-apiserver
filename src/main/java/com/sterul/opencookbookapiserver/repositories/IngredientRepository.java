package com.sterul.opencookbookapiserver.repositories;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sterul.opencookbookapiserver.entities.Ingredient;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;

public interface IngredientRepository extends JpaRepository<Ingredient, Long> {

    Ingredient findByNameAndIsPublicIngredient(String name, boolean isPublicIngredient);

    List<Ingredient> findAllByIsPublicIngredient(boolean isPublicIngredient);

    Ingredient findByNameAndIsPublicIngredientAndOwner(String name, boolean isPublicIngredient, CookpalUser owner);

    List<Ingredient> findAllByIsPublicIngredientAndOwner(boolean isPublicIngredient, CookpalUser owner);

    void deleteAllByOwner(CookpalUser owner);

    List<Ingredient> findAllByIsPublicIngredientAndCreatedOnBefore(boolean isPublicIngredient, Instant createdOn);

}
