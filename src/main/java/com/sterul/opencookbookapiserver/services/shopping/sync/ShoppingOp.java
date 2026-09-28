package com.sterul.opencookbookapiserver.services.shopping.sync;

import java.util.List;

import com.sterul.opencookbookapiserver.entities.catalogue.Aisle;
import com.sterul.opencookbookapiserver.entities.shopping.ItemSource;

/**
 * One change a device made to a list, possibly while offline. Applied in the order the device made
 * them; an op whose id was applied before is skipped, so a retried batch does no harm.
 */
public sealed interface ShoppingOp {

    /** Null for ops the server makes itself, which are never retried. */
    String opId();

    /** Chosen by the device, so it can refer to an item it added offline. */
    String itemId();

    /**
     * Adding a name already on the list asks for more of it.
     *
     * @param aisle null or {@code OTHER} to have the server place it
     */
    record Add(String opId, String itemId, String name, String spec, Aisle aisle, String icon,
            List<ItemSource> sources) implements ShoppingOp {
    }

    /** Null leaves a field as it is; a blank spec clears it; an aisle is a person's choice. */
    record Update(String opId, String itemId, String name, String spec, Aisle aisle) implements ShoppingOp {
    }

    record Buy(String opId, String itemId) implements ShoppingOp {
    }

    record Restore(String opId, String itemId, String spec) implements ShoppingOp {
    }

    record Delete(String opId, String itemId) implements ShoppingOp {
    }
}
