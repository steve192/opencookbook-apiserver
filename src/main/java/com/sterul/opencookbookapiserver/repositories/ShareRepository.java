package com.sterul.opencookbookapiserver.repositories;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sterul.opencookbookapiserver.entities.recipe.Recipe;
import com.sterul.opencookbookapiserver.entities.sharing.Share;
import com.sterul.opencookbookapiserver.entities.sharing.ShareVisibility;

public interface ShareRepository extends JpaRepository<Share, String> {

    Optional<Share> findByRecipeAndVisibility(Recipe recipe, ShareVisibility visibility);

    void deleteByRecipe(Recipe recipe);

    int deleteByExpiresAtBefore(Instant cutoff);

    @Modifying
    @Query("update Share s set s.accessCount = s.accessCount + 1 where s.id = :id")
    void incrementAccessCount(@Param("id") String id);

    @Query("select s from Share s join fetch s.owner join fetch s.recipe order by s.createdOn desc")
    List<Share> findAllForAdministration();

    long countByExpiresAtBefore(Instant cutoff);

    @Query("select coalesce(sum(s.accessCount), 0) from Share s")
    long sumAccessCount();
}
