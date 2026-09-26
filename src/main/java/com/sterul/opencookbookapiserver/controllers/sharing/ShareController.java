package com.sterul.opencookbookapiserver.controllers.sharing;

import java.io.IOException;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.RestController;

import com.sterul.opencookbookapiserver.controllers.BaseController;
import com.sterul.opencookbookapiserver.controllers.responses.RecipeResponse;
import com.sterul.opencookbookapiserver.controllers.sharing.requests.ShareRecipeRequest;
import com.sterul.opencookbookapiserver.controllers.sharing.responses.ShareResponse;
import com.sterul.opencookbookapiserver.controllers.support.RecipeResponses;
import com.sterul.opencookbookapiserver.services.RecipeService;
import com.sterul.opencookbookapiserver.services.SignedInUserService;
import com.sterul.opencookbookapiserver.services.sharing.ShareLinkFactory;
import com.sterul.opencookbookapiserver.services.sharing.ShareService;
import com.sterul.opencookbookapiserver.services.sharing.SharedRecipeImportService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

@RestController
@ConditionalOnProperty(prefix = "opencookbook.sharing", name = "enabled",
        havingValue = "true", matchIfMissing = true)
@RequestMapping(SharePaths.OWNER_BASE)
@Tag(name = "Recipe shares", description = "Sharing your recipes and importing shared ones")
public class ShareController extends BaseController {

    private final ShareService shareService;
    private final SharedRecipeImportService sharedRecipeImportService;
    private final RecipeService recipeService;
    private final ShareLinkFactory shareLinkFactory;
    private final RecipeResponses recipeResponses;

    public ShareController(ShareService shareService, SharedRecipeImportService sharedRecipeImportService,
            RecipeService recipeService, ShareLinkFactory shareLinkFactory, RecipeResponses recipeResponses,
            SignedInUserService signedInUser) {
        super(signedInUser);
        this.shareService = shareService;
        this.sharedRecipeImportService = sharedRecipeImportService;
        this.recipeService = recipeService;
        this.shareLinkFactory = shareLinkFactory;
        this.recipeResponses = recipeResponses;
    }

    @Operation(summary = "The shares of one of your recipes", description = "Empty while the recipe is not shared.")
    @GetMapping
    public List<ShareResponse> getSharesOfRecipe(@RequestParam Long recipeId) {
        requireOwnershipOfRecipe(recipeId);
        return shareService.findPublicRecipeShare(recipeId)
                .map(share -> List.of(ShareResponse.fromEntity(share, shareLinkFactory)))
                .orElseGet(List::of);
    }

    @Operation(summary = "Share a recipe publicly", description = "Returns the existing link if the recipe already has one.")
    @PostMapping
    public ShareResponse shareRecipe(@Valid @RequestBody ShareRecipeRequest request) {
        requireOwnershipOfRecipe(request.recipeId());
        return ShareResponse.fromEntity(shareService.shareRecipePublicly(request.recipeId()),
                shareLinkFactory);
    }

    @Operation(summary = "Stop sharing", description = "The link stops working immediately and permanently.")
    @DeleteMapping("/{" + SharePaths.SHARE_ID_VARIABLE + "}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeShare(@Valid @NotBlank @PathVariable String shareId) {
        shareService.revoke(shareId, getLoggedInUser());
    }

    @Operation(summary = "Import a shared recipe", description = "Copies the shared recipe, its ingredients and its images into your own cookbook.")
    @PostMapping("/{" + SharePaths.SHARE_ID_VARIABLE + "}/import")
    public RecipeResponse importSharedRecipe(@Valid @NotBlank @PathVariable String shareId)
            throws IOException {
        return recipeResponses.of(sharedRecipeImportService.importSharedRecipe(shareId, getLoggedInUser()));
    }

    /**
     * Refuses with "not found" rather than "not allowed", deliberately: answering differently
     * for a recipe that exists but belongs to somebody else would let anyone count the recipes
     * on this server by walking the ids.
     */
    private void requireOwnershipOfRecipe(Long recipeId) {
        recipeService.getOwnRecipe(recipeId, getLoggedInUser());
    }

}
