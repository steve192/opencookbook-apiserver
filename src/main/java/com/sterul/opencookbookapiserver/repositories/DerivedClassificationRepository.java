package com.sterul.opencookbookapiserver.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sterul.opencookbookapiserver.entities.recipe.ClassificationKind;
import com.sterul.opencookbookapiserver.entities.recipe.DerivedClassification;
import com.sterul.opencookbookapiserver.entities.recipe.Recipe;

public interface DerivedClassificationRepository extends JpaRepository<DerivedClassification, Long> {

    @Query("select mark from DerivedClassification mark join fetch mark.run where mark.kind = :kind")
    List<DerivedClassification> findAllOfKind(@Param("kind") ClassificationKind kind);

    Optional<DerivedClassification> findByRecipeAndKind(Recipe recipe, ClassificationKind kind);
}
