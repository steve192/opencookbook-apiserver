package com.sterul.opencookbookapiserver.controllers.shopping.requests;

import java.time.LocalDate;
import java.util.List;

import com.sterul.opencookbookapiserver.entities.shopping.ItemSource;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** @param planDate null for a recipe imported from the recipe screen */
public record ItemSourceRequest(@NotBlank @Size(max = 255) String title, LocalDate planDate) {

    public ItemSource toSource() {
        return new ItemSource(title.strip(), planDate);
    }

    static List<ItemSource> toSources(List<ItemSourceRequest> requests) {
        return requests == null ? List.of() : requests.stream().map(ItemSourceRequest::toSource).toList();
    }
}
