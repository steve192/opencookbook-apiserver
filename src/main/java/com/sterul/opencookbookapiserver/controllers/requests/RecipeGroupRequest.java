package com.sterul.opencookbookapiserver.controllers.requests;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RecipeGroupRequest {

    @NotNull
    private Long id;

    @NotNull
    @NotEmpty
    private String title;

}
