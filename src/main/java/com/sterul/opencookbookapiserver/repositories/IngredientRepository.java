package com.sterul.opencookbookapiserver.repositories;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.sterul.opencookbookapiserver.entities.Ingredient;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.repositories.projections.OwnerCount;

public interface IngredientRepository extends JpaRepository<Ingredient, Long> {

    Ingredient findByNameAndIsPublicIngredient(String name, boolean isPublicIngredient);

    List<Ingredient> findAllByIsPublicIngredient(boolean isPublicIngredient);

    Ingredient findByNameAndIsPublicIngredientAndOwner(String name, boolean isPublicIngredient, CookpalUser owner);

    List<Ingredient> findAllByIsPublicIngredientAndOwner(boolean isPublicIngredient, CookpalUser owner);

    void deleteAllByOwner(CookpalUser owner);

    List<Ingredient> findAllByIsPublicIngredientAndCreatedOnBefore(boolean isPublicIngredient, Instant createdOn);

    @Query("select ingredient.owner.userId as userId, count(ingredient) as count "
            + "from Ingredient ingredient where ingredient.owner is not null "
            + "group by ingredient.owner.userId")
    List<OwnerCount> countGroupedByOwner();

}
