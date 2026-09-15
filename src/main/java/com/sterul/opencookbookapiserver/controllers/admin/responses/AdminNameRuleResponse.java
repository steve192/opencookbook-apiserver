package com.sterul.opencookbookapiserver.controllers.admin.responses;

import java.time.Instant;

import com.sterul.opencookbookapiserver.entities.nutrition.CatalogueNameRule;

public record AdminNameRuleResponse(Long id, String name, CatalogueNameRule.Kind kind, AdminFoodReference food,
        String createdByEmailAddress, Instant createdOn) {

    public static AdminNameRuleResponse fromEntity(CatalogueNameRule rule) {
        var createdBy = rule.getCreatedBy();
        return new AdminNameRuleResponse(rule.getId(), rule.getName(), rule.getKind(), AdminFoodReference.of(rule.getCatalogueFood()),
                createdBy == null ? null : createdBy.getEmailAddress(), rule.getCreatedOn());
    }
}
