package com.sterul.opencookbookapiserver.controllers.shopping.requests;

import java.util.List;

import com.sterul.opencookbookapiserver.entities.catalogue.Aisle;
import com.sterul.opencookbookapiserver.services.shopping.sync.ShoppingOp;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** One op as a device sends it; which fields count depends on the type. */
public record ShoppingOpRequest(
        @NotNull @Pattern(regexp = UUID) String opId,
        @NotNull Type type,
        @NotNull @Pattern(regexp = UUID) String itemId,
        @Size(max = 120) String name,
        @Size(max = 200) String spec,
        Aisle aisle,
        @Pattern(regexp = ICON) String icon,
        @Size(max = 20) List<@Valid ItemSourceRequest> sources) {

    static final String UUID = "[0-9a-fA-F-]{36}";
    static final String ICON = "[a-z0-9_-]{1,64}";

    public enum Type {
        ADD, UPDATE, BUY, RESTORE, DELETE
    }

    public ShoppingOp toOp() {
        return switch (type) {
            case ADD -> new ShoppingOp.Add(opId, itemId, name == null ? "" : name, spec, aisle, icon,
                    ItemSourceRequest.toSources(sources));
            case UPDATE -> new ShoppingOp.Update(opId, itemId, name, spec, aisle);
            case BUY -> new ShoppingOp.Buy(opId, itemId);
            case RESTORE -> new ShoppingOp.Restore(opId, itemId, spec);
            case DELETE -> new ShoppingOp.Delete(opId, itemId);
        };
    }
}
