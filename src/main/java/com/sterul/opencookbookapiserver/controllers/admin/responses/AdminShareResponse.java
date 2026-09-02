package com.sterul.opencookbookapiserver.controllers.admin.responses;

import java.time.Instant;

import com.sterul.opencookbookapiserver.entities.sharing.Share;
import com.sterul.opencookbookapiserver.services.sharing.ShareLinkFactory;

public record AdminShareResponse(
        String shareId,
        String shareUrl,
        Long recipeId,
        String recipeTitle,
        Long ownerUserId,
        String ownerEmailAddress,
        Instant createdOn,
        Instant expiresAt,
        boolean expired,
        long accessCount) {

    public static AdminShareResponse fromEntity(Share share, ShareLinkFactory linkFactory, Instant now) {
        return new AdminShareResponse(
                share.getId(),
                linkFactory.linkTo(share.getId()),
                share.getRecipe().getId(),
                share.getRecipe().getTitle(),
                share.getOwner().getUserId(),
                share.getOwner().getEmailAddress(),
                share.getCreatedOn(),
                share.getExpiresAt(),
                share.hasExpired(now),
                share.getAccessCount());
    }
}
