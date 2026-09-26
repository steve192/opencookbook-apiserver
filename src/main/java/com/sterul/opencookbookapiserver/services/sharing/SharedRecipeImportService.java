package com.sterul.opencookbookapiserver.services.sharing;

import java.io.IOException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.entities.recipe.Recipe;
import com.sterul.opencookbookapiserver.services.RecipeCopier;

/**
 * Copies a shared recipe into somebody else's cookbook. The copying itself is
 * {@link RecipeCopier}'s; what belongs here is that a share link is the authorisation, and that
 * the copy records the link it came through so the origin stays traceable after the link lapses.
 */
@Service
@Transactional
public class SharedRecipeImportService {

    private final ShareService shareService;
    private final RecipeCopier recipeCopier;
    private final ShareLinkFactory shareLinkFactory;

    public SharedRecipeImportService(ShareService shareService, RecipeCopier recipeCopier,
            ShareLinkFactory shareLinkFactory) {
        this.shareService = shareService;
        this.recipeCopier = recipeCopier;
        this.shareLinkFactory = shareLinkFactory;
    }

    public Recipe importSharedRecipe(String shareId, CookpalUser importer) throws IOException {
        var sharedRecipe = shareService.resolveSharedRecipe(shareId);
        return recipeCopier.copyTo(sharedRecipe, importer, shareLinkFactory.linkTo(shareId));
    }
}
