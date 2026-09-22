package com.sterul.opencookbookapiserver.repositories;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.entities.recipe.Diet;
import com.sterul.opencookbookapiserver.entities.recipe.Recipe;
import com.sterul.opencookbookapiserver.entities.recipe.RecipeGroup;
import com.sterul.opencookbookapiserver.repositories.projections.OwnerCount;
import com.sterul.opencookbookapiserver.repositories.projections.RecipeLine;
import com.sterul.opencookbookapiserver.repositories.projections.RecipeTitle;

import jakarta.persistence.LockModeType;

public interface RecipeRepository extends JpaRepository<Recipe, Long> {

    List<Recipe> findByOwner(CookpalUser owner);

    List<Recipe> findByOwnerAndRecipeTypeIn(CookpalUser owner, List<Diet> recipeType);

    List<Recipe> findByRecipeGroups(RecipeGroup recipeGroup);

    /**
     * Loads a recipe and holds it against concurrent writers until the transaction ends, for
     * decisions made by reading and then writing.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Recipe> findForUpdateById(Long id);

    @Query("select distinct recipe from Recipe recipe join recipe.neededIngredients need where need.ingredient.id in :ingredientIds")
    List<Recipe> findUsingIngredients(@Param("ingredientIds") Collection<Long> ingredientIds, Pageable page);

    @Query("select new com.sterul.opencookbookapiserver.repositories.projections.RecipeLine(recipe.id, recipe.title, need) "
            + "from Recipe recipe join recipe.neededIngredients need where need.ingredient.id in :ingredientIds")
    List<RecipeLine> findLinesUsingIngredients(@Param("ingredientIds") Collection<Long> ingredientIds);

    @Query("select distinct recipe from Recipe recipe left join fetch recipe.neededIngredients need "
            + "left join fetch need.ingredient ingredient left join fetch ingredient.catalogueFood")
    List<Recipe> findAllWithIngredients();

    /**
     * These owners' recipes with everything suggestion and planning match on. A pool is small enough
     * to narrow in memory, which keeps the matching rules in one place instead of also in JPQL.
     */
    @Query("select distinct recipe from Recipe recipe left join fetch recipe.neededIngredients need "
            + "left join fetch need.ingredient ingredient left join fetch ingredient.catalogueFood "
            + "where recipe.owner.userId in :ownerIds")
    List<Recipe> findByOwnersWithIngredients(@Param("ownerIds") Collection<Long> ownerIds);

    @Query("select recipe.owner.userId as userId, count(recipe) as count from Recipe recipe "
            + "where recipe.owner is not null group by recipe.owner.userId")
    List<OwnerCount> countGroupedByOwner();

    /** By id after title, so equal titles cannot swap places between two pages. */
    Slice<Recipe> findByOwnerUserIdInOrderByTitleAscIdAsc(Collection<Long> ownerIds, Pageable page);

    @Query("select new com.sterul.opencookbookapiserver.repositories.projections.RecipeTitle(recipe.id, recipe.title) "
            + "from Recipe recipe where recipe.owner.userId in :ownerIds order by recipe.title, recipe.id")
    List<RecipeTitle> findTitlesByOwners(@Param("ownerIds") Collection<Long> ownerIds);

    List<Recipe> findByIdInAndOwnerUserIdIn(Collection<Long> ids, Collection<Long> ownerIds);

    long countByOwnerUserIdIn(Collection<Long> ownerIds);
}