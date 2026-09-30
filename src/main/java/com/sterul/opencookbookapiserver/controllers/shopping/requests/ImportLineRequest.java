package com.sterul.opencookbookapiserver.controllers.shopping.requests;

import java.util.List;

import com.sterul.opencookbookapiserver.entities.catalogue.Aisle;
import com.sterul.opencookbookapiserver.services.shopping.sync.ShoppingOp;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** A line as the import sheet finished it: merged, scaled, and placed by the preview. */
public record ImportLineRequest(
        @NotBlank @Size(max = 120) String name,
        @Size(max = 200) String spec,
        Aisle aisle,
        @Pattern(regexp = ShoppingOpRequest.ICON_PATTERN) String icon,
        @Size(max = 20) List<@Valid ItemSourceRequest> sources) {

    public ShoppingOp.Add toAdd() {
        return new ShoppingOp.Add(null, null, name, spec, aisle, icon, ItemSourceRequest.toSources(sources), false);
    }
}
