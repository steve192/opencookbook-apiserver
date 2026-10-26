package com.sterul.opencookbookapiserver.services.shopping.history;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.sterul.opencookbookapiserver.entities.shopping.ItemChange;
import com.sterul.opencookbookapiserver.services.shopping.sync.ShoppingItemChanged;
import com.sterul.opencookbookapiserver.services.shopping.sync.ShoppingItemSlipped;
import com.sterul.opencookbookapiserver.services.shopping.sync.ShoppingPurchaseTakenBack;

/** Records what people change on items in the same transaction, and has the regular items learn from it. */
@Component
class ShoppingHistoryListener {

    private final ShoppingHistory history;
    private final ShoppingRegulars regulars;

    ShoppingHistoryListener(ShoppingHistory history, ShoppingRegulars regulars) {
        this.history = history;
        this.regulars = regulars;
    }

    @EventListener
    void on(ShoppingItemChanged changed) {
        history.remember(changed);
        if (changed.kind() == ItemChange.BOUGHT) {
            regulars.bought(changed.list(), changed.item(), history.purchases(changed.list(), changed.item().getNameKey()));
        }
    }

    @EventListener
    void on(ShoppingItemSlipped slipped) {
        history.forgetSince(slipped.list(), slipped.itemId(), slipped.listedAt());
    }

    @EventListener
    void on(ShoppingPurchaseTakenBack takenBack) {
        history.takeBack(takenBack.list(), takenBack.nameKey(), takenBack.boughtAt());
        regulars.purchaseTakenBack(takenBack.list(), takenBack.nameKey(),
                history.purchases(takenBack.list(), takenBack.nameKey()));
    }
}
