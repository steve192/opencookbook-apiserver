package com.sterul.opencookbookapiserver.controllers;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sterul.opencookbookapiserver.controllers.exceptions.NotAuthorizedException;
import com.sterul.opencookbookapiserver.controllers.requests.ShareRequest;
import com.sterul.opencookbookapiserver.controllers.responses.RecipeResponse;
import com.sterul.opencookbookapiserver.controllers.responses.RecipeShareResponse;
import com.sterul.opencookbookapiserver.entities.RecipeShare;
import com.sterul.opencookbookapiserver.services.RecipeService;
import com.sterul.opencookbookapiserver.services.RecipeSharingService;
import com.sterul.opencookbookapiserver.services.exceptions.ElementNotFound;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/v1/sharing")
@Tag(name = "Recipe shares", description = "Managing recipe shares")
@Slf4j
public class RecipeShareController extends BaseController {

    RecipeSharingService recipeSharingService;
    RecipeService recipeService;

    public RecipeShareController(RecipeSharingService recipeSharingService, RecipeService recipeService) {
        this.recipeSharingService = recipeSharingService;
        this.recipeService = recipeService;
    }

    @Operation(summary = "Share recipe publicly and get share id")
    @PostMapping(value = "/share-publicly")
    public RecipeShareResponse sharePublicly(@Valid @NotBlank @RequestBody ShareRequest shareRequest)
            throws NotAuthorizedException, ElementNotFound {

        if (!recipeService.hasAccessPermissionToRecipe(shareRequest.getRecipeId(), getLoggedInUser())) {
            throw new NotAuthorizedException();
        }
        var recipe = recipeService.getRecipeById(shareRequest.getRecipeId());

        return entityToResponse(recipeSharingService.shareRecipePublicly(recipe, getLoggedInUser()));

    }

    @Operation(summary = "Unshare recipe publicly")
    @PostMapping(value = "/unshare-publicly/{shareId}")
    public void unsharePublicly(@PathVariable String shareId)
            throws NotAuthorizedException, ElementNotFound {

        var share = recipeSharingService.getRecipeShareByShareId(shareId, false);
        if (!share.getOwner().equals(getLoggedInUser())) {
            throw new NotAuthorizedException();
        }

        recipeSharingService.unshareRecipePublicly(share.getRecipe());
    }

    @Operation(summary = "Get recipe by share id")
    @GetMapping(value = "/{shareId}")
    public RecipeResponse getRecipeByShareId(@PathVariable String shareId)
            throws ElementNotFound {

        var recipeShare = recipeSharingService.getRecipeShareByShareId(shareId, true);
        var recipe = recipeService.getRecipeById(recipeShare.getRecipe().getId());

        return RecipeResponse.fromEntity(recipe);
    }

    private RecipeShareResponse entityToResponse(RecipeShare recipeShare) {
        return RecipeShareResponse.builder()
                .shareId(recipeShare.getUuid())
                .recipeId(recipeShare.getRecipe().getId())
                .accessCount(recipeShare.getAccessCount())
                .build();
    }
}
