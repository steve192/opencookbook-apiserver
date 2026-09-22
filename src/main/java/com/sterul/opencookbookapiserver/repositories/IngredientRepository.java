package com.sterul.opencookbookapiserver.repositories;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sterul.opencookbookapiserver.entities.Ingredient;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.entities.nutrition.CatalogueFood;
import com.sterul.opencookbookapiserver.repositories.projections.IngredientUseCount;
import com.sterul.opencookbookapiserver.repositories.projections.OwnerCount;

public interface IngredientRepository extends JpaRepository<Ingredient, Long> {

    Optional<Ingredient> findByNameAndOwner(String name, CookpalUser owner);

    Optional<Ingredient> findByIdAndOwner(Long id, CookpalUser owner);

    /** Ids belonging to somebody else are simply absent from the result. */
    List<Ingredient> findAllByIdInAndOwnerUserIdIn(Collection<Long> ids, Collection<Long> ownerIds);

    List<Ingredient> findAllByOwner(CookpalUser owner);

    /**
     * Ingredients created before a moment that nothing uses. Planning profiles count: an avoided
     * ingredient is usually in no recipe, and deleting it would cascade it out of the profile.
     */
    @Query("select ingredient from Ingredient ingredient where ingredient.createdOn < :createdBefore "
            + "and not exists (select need from IngredientNeed need where need.ingredient = ingredient) "
            + "and ingredient.id not in (select avoided from PlanningProfile profile "
            + "join profile.avoidedIngredientIds avoided) "
            + "and ingredient.id not in (select pantryItem.ingredientId from PlanningProfile profile "
            + "join profile.pantry pantryItem)")
    List<Ingredient> findUnusedCreatedBefore(@Param("createdBefore") Instant createdBefore);

    long countByCatalogueFood(CatalogueFood catalogueFood);

    @Query("select count(need) > 0 from IngredientNeed need where need.ingredient = :ingredient")
    boolean isUsedByARecipe(@Param("ingredient") Ingredient ingredient);

    void deleteAllByOwner(CookpalUser owner);

    /** Moves every link from one catalogue food to another, keeping who decided it. */
    @Modifying
    @Query("update Ingredient ingredient set ingredient.catalogueFood = :target where ingredient.catalogueFood = :source")
    int relink(@Param("source") CatalogueFood source, @Param("target") CatalogueFood target);

    // Relink run scopes: never ingredients a person decided.
    String AUTOMATICALLY_DECIDED = "select ingredient from Ingredient ingredient join fetch ingredient.owner "
            + "left join fetch ingredient.catalogueFood where ingredient.excludedFromNutrition = false and ";
    String LINKED_AUTOMATICALLY = "ingredient.linkSource = com.sterul.opencookbookapiserver.entities.Ingredient.LinkSource.AUTO";

    @Query(AUTOMATICALLY_DECIDED + "ingredient.catalogueFood is null "
            + "and (ingredient.linkSource is null or " + LINKED_AUTOMATICALLY + ")")
    List<Ingredient> findNeverMatchedOrUnlinked();

    @Query(AUTOMATICALLY_DECIDED + LINKED_AUTOMATICALLY
            + " and ingredient.catalogueFood is not null and ingredient.linkConfidence < :confidence")
    List<Ingredient> findLinkedAutomaticallyBelow(@Param("confidence") float confidence);

    @Query(AUTOMATICALLY_DECIDED + LINKED_AUTOMATICALLY)
    List<Ingredient> findLinkedAutomatically();

    @Query(AUTOMATICALLY_DECIDED + LINKED_AUTOMATICALLY
            + " and ingredient.catalogueFood.retired = true")
    List<Ingredient> findLinkedAutomaticallyToRetiredFoods();

    @Query(AUTOMATICALLY_DECIDED + LINKED_AUTOMATICALLY
            + " and ingredient.linkMatcherVersion < :version")
    List<Ingredient> findLinkedAutomaticallyBefore(@Param("version") int matcherVersion);

    @Query("select ingredient from Ingredient ingredient join fetch ingredient.owner left join fetch ingredient.catalogueFood")
    List<Ingredient> findAllWithOwnerAndFood();

    /** Unused ingredients are absent. */
    @Query("select need.ingredient.id as ingredientId, count(need) as count from IngredientNeed need group by need.ingredient.id")
    List<IngredientUseCount> countUsesGroupedByIngredient();

    @Query("select ingredient.owner.userId as userId, count(ingredient) as count "
            + "from Ingredient ingredient group by ingredient.owner.userId")
    List<OwnerCount> countGroupedByOwner();
}
