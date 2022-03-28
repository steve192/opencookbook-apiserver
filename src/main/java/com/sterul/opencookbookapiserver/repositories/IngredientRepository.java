package com.sterul.opencookbookapiserver.repositories;

import com.sterul.opencookbookapiserver.entities.Ingredient;
import com.sterul.opencookbookapiserver.entities.account.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IngredientRepository extends JpaRepository<Ingredient, Long> {

    Ingredient findByNameAndIsPublicIngredient(String name, boolean isPublicIngredient);

    List<Ingredient> findAllByIsPublicIngredient(boolean isPublicIngredient);

    Ingredient findByNameAndIsPublicIngredientAndOwner(String name, boolean isPublicIngredient, User owner);

    List<Ingredient> findAllByIsPublicIngredientAndOwner(boolean isPublicIngredient, User owner);

    void deleteAllByOwner(User owner);


}
