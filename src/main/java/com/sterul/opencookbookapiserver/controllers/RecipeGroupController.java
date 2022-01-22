package com.sterul.opencookbookapiserver.controllers;

import java.util.List;

import com.sterul.opencookbookapiserver.controllers.exceptions.NotAuthorizedException;
import com.sterul.opencookbookapiserver.controllers.requests.RecipeGroupRequest;
import com.sterul.opencookbookapiserver.controllers.responses.RecipeGroupResponse;
import com.sterul.opencookbookapiserver.entities.recipe.RecipeGroup;
import com.sterul.opencookbookapiserver.services.RecipeGroupService;
import com.sterul.opencookbookapiserver.services.exceptions.ElementNotFound;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/recipe-groups")
public class RecipeGroupController extends BaseController {

    @Autowired
    private RecipeGroupService recipeGroupService;

    @GetMapping("")
    public List<RecipeGroupResponse> getAll() {
        return recipeGroupService.getRecipeGroupsByOwner(getLoggedInUser()).stream()
                .map(this::entityToResponse)
                .toList();
    }

    @PostMapping("")
    public RecipeGroupResponse create(@RequestBody RecipeGroupRequest recipeGroup) {
        var recipeGroupEntity = requestToEntity(recipeGroup);
        recipeGroupEntity.setOwner(getLoggedInUser());
        recipeGroupEntity.setId(null);
        return entityToResponse(recipeGroupService.createRecipeGroup(recipeGroupEntity));
    }

    @PutMapping("/{id}")
    public RecipeGroupResponse change(@PathVariable Long id, @RequestBody RecipeGroupRequest updatedRecipeGroup)
            throws NotAuthorizedException, ElementNotFound {

        if (!recipeGroupService.hasAccessPermissionToRecipeGroup(id, getLoggedInUser())) {
            throw new NotAuthorizedException();
        }
        var groupEntity = requestToEntity(updatedRecipeGroup);
        groupEntity.setId(id);

        return entityToResponse(recipeGroupService.updateRecipeGroup(groupEntity));
    }

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
