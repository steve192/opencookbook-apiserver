package com.sterul.opencookbookapiserver.controllers.requests;

import java.util.ArrayList;
import java.util.List;

import jakarta.validation.Valid;

import com.sterul.opencookbookapiserver.controllers.dto.RecipeBaseDTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@Data
@EqualsAndHashCode(callSuper = true)
public class RecipeRequest extends RecipeBaseDTO {

    @Builder.Default
    @Valid
    private List<RecipeGroupRequest> recipeGroups = new ArrayList<>();
}