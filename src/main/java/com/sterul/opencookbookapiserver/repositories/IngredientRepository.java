package com.sterul.opencookbookapiserver.repositories;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sterul.opencookbookapiserver.repositories.entities.Ingredient;
import com.sterul.opencookbookapiserver.repositories.entities.account.User;

public interface IngredientRepository extends JpaRepository<Ingredient, Long> {

    Ingredient findByNameAndIsPublicIngredient(String name, boolean isPublicIngredient);

    List<Ingredient> findAllByIsPublicIngredient(boolean isPublicIngredient);

    Ingredient findByNameAndIsPublicIngredientAndOwner(String name, boolean isPublicIngredient, User owner);

    List<Ingredient> findAllByIsPublicIngredientAndOwner(boolean isPublicIngredient, User owner);

    void deleteAllByOwner(User owner);

    List<Ingredient> findAllByIsPublicIngredientAndCreatedOnBefore(boolean isPublicIngredient, Instant createdOn);

}
