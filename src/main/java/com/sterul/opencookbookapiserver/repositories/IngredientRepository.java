package com.sterul.opencookbookapiserver.repositories;

import com.sterul.opencookbookapiserver.entities.Ingredient;
import com.sterul.opencookbookapiserver.entities.account.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IngredientRepository extends JpaRepository<Ingredient, Long> {

    Ingredient findByNameAndIsPublicIngredient(String name, boolean isPublicIngredient);

    Ingredient findByNameAndIsPublicIngredientAndOwner(String name, boolean isPublicIngredient, User owner);

    void deleteAllByOwner(User owner);


}
