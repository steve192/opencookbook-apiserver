package com.sterul.opencookbookapiserver.controllers.admin.responses;

import java.time.Instant;
import java.util.List;

import com.sterul.opencookbookapiserver.entities.BringExport;

public record AdminBringExportResponse(
        String id,
        Long ownerUserId,
        String ownerEmailAddress,
        int baseAmount,
        List<String> ingredients,
        int ingredientCount,
        Instant createdOn,
        Instant expiresAt,
        boolean expired) {

    public static AdminBringExportResponse fromEntity(BringExport export, Instant now) {
        var owner = export.getOwner();
        return new AdminBringExportResponse(
                export.getId(),
                owner == null ? null : owner.getUserId(),
                owner == null ? null : owner.getEmailAddress(),
                export.getBaseAmount(),
                List.copyOf(export.getIngredients()),
                export.getIngredients().size(),
                export.getCreatedOn(),
                export.getExpiresAt(),
                export.hasExpired(now));
    }
}
