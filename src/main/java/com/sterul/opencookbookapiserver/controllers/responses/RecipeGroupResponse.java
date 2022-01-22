package com.sterul.opencookbookapiserver.controllers.responses;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RecipeGroupResponse {
    private Long id;
    private String title;
}
