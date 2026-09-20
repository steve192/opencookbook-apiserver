package com.sterul.opencookbookapiserver.repositories;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sterul.opencookbookapiserver.entities.recipe.RecipeClassificationProposal;
import com.sterul.opencookbookapiserver.entities.recipe.RecipeClassificationRun;

public interface RecipeClassificationProposalRepository extends JpaRepository<RecipeClassificationProposal, Long> {

    @Query("select proposal from RecipeClassificationProposal proposal join fetch proposal.recipe "
            + "where proposal.run = :run order by proposal.id")
    List<RecipeClassificationProposal> findAllOf(@Param("run") RecipeClassificationRun run);

    List<RecipeClassificationProposal> findAllByRunAndIdIn(RecipeClassificationRun run, Collection<Long> ids);

    List<RecipeClassificationProposal> findAllByRunAndDecision(RecipeClassificationRun run,
            RecipeClassificationProposal.Decision decision);
}
