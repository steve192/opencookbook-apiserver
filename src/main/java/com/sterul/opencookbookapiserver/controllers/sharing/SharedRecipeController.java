package com.sterul.opencookbookapiserver.controllers.sharing;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.RestController;

import com.sterul.opencookbookapiserver.controllers.sharing.responses.SharedRecipeResponse;
import com.sterul.opencookbookapiserver.controllers.support.RecipeImageResponses;
import com.sterul.opencookbookapiserver.services.RecipeImageService;
import com.sterul.opencookbookapiserver.services.exceptions.ElementNotFound;
import com.sterul.opencookbookapiserver.services.sharing.ShareService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

/**
 * Everything a recipient of a share link may do, which is read.
 *
 * Served without authentication, so every response has to be assumed to reach anybody. Two rules
 * follow, both load bearing: nothing mapped here writes, and nothing mapped here returns an
 * entity, only {@link SharedRecipeResponse}, which was built to be published.
 */
@RestController
@ConditionalOnProperty(prefix = "opencookbook.sharing", name = "enabled",
        havingValue = "true", matchIfMissing = true)
@RequestMapping(SharePaths.PUBLIC_BASE)
@Tag(name = "Shared recipes", description = "Reading a recipe somebody shared with you")
public class SharedRecipeController {

    private final ShareService shareService;
    private final RecipeImageService recipeImageService;

    public SharedRecipeController(ShareService shareService, RecipeImageService recipeImageService) {
        this.shareService = shareService;
        this.recipeImageService = recipeImageService;
    }

    @Operation(summary = "Read a shared recipe", description = "No authentication required. Rate limited per client and per share.")
    @GetMapping("/{" + SharePaths.SHARE_ID_VARIABLE + "}")
    public SharedRecipeResponse getSharedRecipe(@Valid @NotBlank @PathVariable String shareId)
            throws ElementNotFound {
        return SharedRecipeResponse.fromEntity(shareService.openSharedRecipe(shareId));
    }

    @Operation(summary = "Read an image of a shared recipe")
    @GetMapping(value = "/{" + SharePaths.SHARE_ID_VARIABLE + "}/images/{uuid}", produces = MediaType.IMAGE_JPEG_VALUE)
    public ResponseEntity<byte[]> getSharedRecipeImage(
            @Valid @NotBlank @PathVariable String shareId,
            @Valid @NotBlank @PathVariable String uuid) throws ElementNotFound {

        shareService.requireSharedImage(shareId, uuid);
        return RecipeImageResponses.servePublicly(() -> recipeImageService.getImage(uuid), uuid);
    }

    @Operation(summary = "Read an image thumbnail of a shared recipe")
    @GetMapping(value = "/{" + SharePaths.SHARE_ID_VARIABLE
            + "}/images/thumbnail/{uuid}", produces = MediaType.IMAGE_JPEG_VALUE)
    public ResponseEntity<byte[]> getSharedRecipeThumbnail(
            @Valid @NotBlank @PathVariable String shareId,
            @Valid @NotBlank @PathVariable String uuid) throws ElementNotFound {

        shareService.requireSharedImage(shareId, uuid);
        return RecipeImageResponses.servePublicly(() -> recipeImageService.getThumbnailImage(uuid), uuid);
    }
}
