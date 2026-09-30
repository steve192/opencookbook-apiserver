package com.sterul.opencookbookapiserver.services.shopping.sync;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.entities.catalogue.Aisle;
import com.sterul.opencookbookapiserver.entities.shopping.ItemStatus;
import com.sterul.opencookbookapiserver.entities.shopping.ShoppingItem;
import com.sterul.opencookbookapiserver.entities.shopping.ShoppingList;
import com.sterul.opencookbookapiserver.repositories.ShoppingItemRepository;
import com.sterul.opencookbookapiserver.services.shopping.ShoppingNames;
import com.sterul.opencookbookapiserver.services.shopping.placement.ItemPlacement;
import com.sterul.opencookbookapiserver.services.shopping.placement.ItemPlacer;
import com.sterul.opencookbookapiserver.services.shopping.placement.PlacementQuery;

/**
 * Applies one op to a list the caller holds locked. An op that no longer fits - its item was
 * deleted meanwhile, or the list is full - is dropped rather than failing the batch, since a device
 * cannot take back what it did offline.
 */
@Component
class ItemEditor {

    static final int MAX_ACTIVE_ITEMS = 500;

    private final ShoppingItemRepository itemRepository;
    private final ItemPlacer placer;
    private final Clock clock;

    ItemEditor(ShoppingItemRepository itemRepository, ItemPlacer placer, Clock clock) {
        this.itemRepository = itemRepository;
        this.placer = placer;
        this.clock = clock;
    }

    void apply(ShoppingList list, ShoppingOp op, CookpalUser actor) {
        switch (op) {
            case ShoppingOp.Add add -> add(list, add, actor);
            case ShoppingOp.Update update -> itemOf(list, update.itemId()).forEach(item -> update(list, item, update, actor));
            case ShoppingOp.Buy buy -> itemOf(list, buy.itemId()).stream().filter(ShoppingItem::isActive)
                    .forEach(item -> stamp(list, item, () -> item.buy(clock.instant())));
            case ShoppingOp.Restore restore -> itemOf(list, restore.itemId()).stream().filter(item -> !item.isActive())
                    .forEach(item -> stamp(list, item,
                            () -> item.reactivate(restore.spec(), List.of(), clock.instant())));
            case ShoppingOp.Delete delete -> itemOf(list, delete.itemId())
                    .forEach(item -> stamp(list, item, item::delete));
        }
    }

    private void add(ShoppingList list, ShoppingOp.Add add, CookpalUser actor) {
        var key = ShoppingNames.key(add.name());
        if (key.isEmpty()) {
            return;
        }
        var existing = itemRepository.findByListAndNameKeyAndDeletedFalse(list, key);
        if (existing.isPresent()) {
            var item = existing.get();
            stamp(list, item, () -> {
                if (item.isActive()) {
                    item.addMore(add.spec(), add.sources(), clock.instant());
                } else {
                    item.reactivate(add.spec(), add.sources(), clock.instant());
                }
                if (add.prioritized()) {
                    item.setPrioritized(true);
                }
            });
            return;
        }
        if (itemRepository.existsById(add.itemId())
                || itemRepository.countByListAndStatusAndDeletedFalse(list, ItemStatus.ACTIVE) >= MAX_ACTIVE_ITEMS) {
            return;
        }
        var placement = add.aisle() == null || add.aisle() == Aisle.OTHER
                ? placeTyped(add.name(), actor)
                : new ItemPlacement(add.aisle(), add.icon());
        var item = ShoppingItem.builder()
                .id(add.itemId()).list(list).name(add.name().strip()).nameKey(key)
                .aisle(placement.aisle()).icon(placement.icon())
                .status(ItemStatus.ACTIVE).prioritized(add.prioritized()).addedBy(actor).addedAt(clock.instant())
                .sources(new ArrayList<>(add.sources()))
                .build();
        stamp(list, item, () -> item.replaceSpec(add.spec()));
    }

    private void update(ShoppingList list, ShoppingItem item, ShoppingOp.Update update, CookpalUser actor) {
        stamp(list, item, () -> {
            if (update.name() != null && !update.name().isBlank()) {
                rename(list, item, update.name(), actor);
            }
            if (update.spec() != null) {
                item.replaceSpec(update.spec());
            }
            if (update.aisle() != null) {
                item.placeManually(update.aisle());
            }
            if (update.prioritized() != null) {
                item.setPrioritized(update.prioritized());
            }
        });
    }

    /** Onto a name another item already has, the rename is dropped: the list holds each name once. */
    private void rename(ShoppingList list, ShoppingItem item, String name, CookpalUser actor) {
        var key = ShoppingNames.key(name);
        if (!key.equals(item.getNameKey()) && itemRepository.findByListAndNameKeyAndDeletedFalse(list, key).isPresent()) {
            return;
        }
        item.rename(name.strip(), key);
        if (!item.isAisleManual()) {
            var placement = placeTyped(name, actor);
            item.place(placement.aisle(), placement.icon());
        }
    }

    private ItemPlacement placeTyped(String name, CookpalUser actor) {
        return placer.place(PlacementQuery.typed(name, actor.getLanguage()));
    }

    private List<ShoppingItem> itemOf(ShoppingList list, String itemId) {
        return itemRepository.findByIdAndListAndDeletedFalse(itemId, list).stream().toList();
    }

    private void stamp(ShoppingList list, ShoppingItem item, Runnable change) {
        change.run();
        item.setVersion(list.nextVersion());
        itemRepository.save(item);
    }
}
