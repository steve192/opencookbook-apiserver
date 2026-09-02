package com.sterul.opencookbookapiserver.controllers.sharing.responses;

import java.time.Instant;

import com.sterul.opencookbookapiserver.entities.sharing.Share;
import com.sterul.opencookbookapiserver.services.sharing.ShareLinkFactory;

public record ShareResponse(
        String shareId,
        String shareUrl,
        Long recipeId,
        Instant expiresAt,
        long accessCount) {

    public static ShareResponse fromEntity(Share share, ShareLinkFactory linkFactory) {
        return new ShareResponse(
                share.getId(),
                linkFactory.linkTo(share.getId()),
                share.getRecipe().getId(),
                share.getExpiresAt(),
                share.getAccessCount());
    }
}
