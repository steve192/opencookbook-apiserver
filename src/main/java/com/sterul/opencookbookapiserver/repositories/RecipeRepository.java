package com.sterul.opencookbookapiserver.repositories;

import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.entities.recipe.Recipe;
import com.sterul.opencookbookapiserver.entities.recipe.RecipeGroup;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;
import java.util.Optional;

public interface RecipeRepository extends JpaRepository<Recipe, Long> {

    List<Recipe> findByTitleIgnoreCaseContaining(String searchString);

    List<Recipe> findByOwner(CookpalUser owner);

    List<Recipe> findByOwnerAndRecipeTypeIn(CookpalUser owner, List<Recipe.RecipeType> recipeType);

    List<Recipe> findByRecipeGroups(RecipeGroup recipeGroup);

    /**
     * Loads a recipe and holds it against concurrent writers until the transaction ends, for
     * decisions made by reading and then writing.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Recipe> findForUpdateById(Long id);
}