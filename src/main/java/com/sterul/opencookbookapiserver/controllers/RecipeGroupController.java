package com.sterul.opencookbookapiserver.controllers;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sterul.opencookbookapiserver.controllers.exceptions.NotAuthorizedException;
import com.sterul.opencookbookapiserver.controllers.requests.RecipeGroupRequest;
import com.sterul.opencookbookapiserver.controllers.responses.RecipeGroupResponse;
import com.sterul.opencookbookapiserver.entities.recipe.RecipeGroup;
import com.sterul.opencookbookapiserver.services.RecipeGroupService;
import com.sterul.opencookbookapiserver.services.exceptions.ElementNotFound;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/recipe-groups")
@Tag(name = "Recipe groups", description = "Recipe groups")
public class RecipeGroupController extends BaseController {

    @Autowired
    private RecipeGroupService recipeGroupService;

    @Operation(summary = "Get all recipe groups")
    @GetMapping("")
    public List<RecipeGroupResponse> getAll() {
        return recipeGroupService.getRecipeGroupsByOwner(getLoggedInUser()).stream()
                .map(this::entityToResponse)
                .toList();
    }

    @Operation(summary = "Create a recipe group")
    @PostMapping("")
    public RecipeGroupResponse create(@Valid @RequestBody RecipeGroupRequest recipeGroup) {
        var recipeGroupEntity = requestToEntity(recipeGroup);
        recipeGroupEntity.setOwner(getLoggedInUser());
        recipeGroupEntity.setId(null);
        return entityToResponse(recipeGroupService.createRecipeGroup(recipeGroupEntity));
    }

    @Operation(summary = "Change a recipe group")
    @PutMapping("/{id}")
    public RecipeGroupResponse change(@PathVariable Long id, @Valid @RequestBody RecipeGroupRequest updatedRecipeGroup)
            throws NotAuthorizedException, ElementNotFound {

        if (!recipeGroupService.hasAccessPermissionToRecipeGroup(id, getLoggedInUser())) {
            throw new NotAuthorizedException();
        }
        var groupEntity = requestToEntity(updatedRecipeGroup);
        groupEntity.setId(id);

        return entityToResponse(recipeGroupService.updateRecipeGroup(groupEntity));
    }

    @Operation(summary = "Delete a recipe group", description = "Assigned recipes will not be deleted, but the recipe group will be removed from them")
    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) throws NotAuthorizedException, ElementNotFound {
        if (!recipeGroupService.hasAccessPermissionToRecipeGroup(id, getLoggedInUser())) {
            throw new NotAuthorizedException();
        }

        recipeGroupService.deleteRecipeGroup(id);
    }

    private RecipeGroupResponse entityToResponse(RecipeGroup recipeGroup) {
        return RecipeGroupResponse.builder()
                .id(recipeGroup.getId())
                .title(recipeGroup.getTitle())
                .build();
    }

    private RecipeGroup requestToEntity(RecipeGroupRequest recipeGroupRequest) {
        return RecipeGroup.builder()
                .id(recipeGroupRequest.getId())
                .title(recipeGroupRequest.getTitle())
                .build();
    }
}
