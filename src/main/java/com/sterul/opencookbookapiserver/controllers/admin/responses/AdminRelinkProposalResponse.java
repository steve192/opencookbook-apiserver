package com.sterul.opencookbookapiserver.controllers.admin.responses;

import com.sterul.opencookbookapiserver.entities.nutrition.IngredientRelinkProposal;
import com.sterul.opencookbookapiserver.services.nutrition.matching.ConfidenceBand;

/** @param band null when the proposal unlinks */
public record AdminRelinkProposalResponse(
        Long id,
        String name,
        String language,
        int ingredientCount,
        AdminFoodReference oldFood,
        AdminFoodReference newFood,
        Float newConfidence,
        ConfidenceBand band,
        IngredientRelinkProposal.Change change,
        IngredientRelinkProposal.Decision decision) {

    public static AdminRelinkProposalResponse fromEntity(IngredientRelinkProposal proposal) {
        var confidence = proposal.getNewConfidence();
        return new AdminRelinkProposalResponse(proposal.getId(), proposal.getName(), proposal.getLanguage(),
                proposal.getMembers().size(), AdminFoodReference.of(proposal.getOldFood()), AdminFoodReference.of(proposal.getNewFood()),
                confidence, confidence == null ? null : ConfidenceBand.of(confidence), proposal.getChange(), proposal.getDecision());
    }
}
