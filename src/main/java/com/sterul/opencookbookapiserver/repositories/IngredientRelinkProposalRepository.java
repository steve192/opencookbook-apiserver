package com.sterul.opencookbookapiserver.repositories;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.sterul.opencookbookapiserver.entities.nutrition.IngredientRelinkProposal;
import com.sterul.opencookbookapiserver.entities.nutrition.IngredientRelinkRun;

public interface IngredientRelinkProposalRepository extends JpaRepository<IngredientRelinkProposal, Long> {

    @EntityGraph(attributePaths = {"members", "oldFood", "newFood"})
    List<IngredientRelinkProposal> findAllByRunOrderByNameAsc(IngredientRelinkRun run);

    @EntityGraph(attributePaths = {"members", "oldFood", "newFood"})
    List<IngredientRelinkProposal> findAllByRunAndDecision(IngredientRelinkRun run, IngredientRelinkProposal.Decision decision);

    @EntityGraph(attributePaths = {"members", "oldFood", "newFood"})
    List<IngredientRelinkProposal> findAllByRunAndIdIn(IngredientRelinkRun run, Collection<Long> ids);
}
