package com.sterul.opencookbookapiserver.services.shopping.sync;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.entities.catalogue.Aisle;
import com.sterul.opencookbookapiserver.entities.shopping.ItemChange;
import com.sterul.opencookbookapiserver.entities.shopping.ItemSource;
import com.sterul.opencookbookapiserver.entities.shopping.ItemStatus;
import com.sterul.opencookbookapiserver.entities.shopping.ShoppingItem;
import com.sterul.opencookbookapiserver.entities.shopping.ShoppingList;
import com.sterul.opencookbookapiserver.repositories.ShoppingItemRepository;
import com.sterul.opencookbookapiserver.services.shopping.ShoppingNames;
import com.sterul.opencookbookapiserver.services.shopping.placement.ItemPlacement;
import com.sterul.opencookbookapiserver.services.shopping.placement.ItemPlacer;
import com.sterul.opencookbookapiserver.services.shopping.placement.PlacementQuery;

/**
 * Applies one op to a list the caller holds locked, and tells the list's history what a person changed. An op
 * that no longer fits - its item was deleted meanwhile, or the list is full - is dropped rather than failing the
 * batch, since a device cannot take back what it did offline.
 */
@Component
class ItemEditor {

    static final int MAX_ACTIVE_ITEMS = 500;
    /** As long as a phone is plausibly offline; a device clock claiming an earlier time is wrong. */
    static final Duration TRUSTED_DEVICE_PAST = Duration.ofDays(30);

    private final ShoppingItemRepository itemRepository;
    private final ItemPlacer placer;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    ItemEditor(ShoppingItemRepository itemRepository, ItemPlacer placer, ApplicationEventPublisher events,
            Clock clock) {
        this.itemRepository = itemRepository;
        this.placer = placer;
        this.events = events;
        this.clock = clock;
    }

    /** @param actor null for the server's own ops, which the history does not record */
    private record Edit(ShoppingList list, Instant at, CookpalUser actor) {
    }

    void apply(ShoppingList list, ShoppingOp op, CookpalUser actor) {
        var edit = new Edit(list, momentOf(op), actor);
        switch (op) {
            case ShoppingOp.Add add -> add(edit, add);
            case ShoppingOp.Update update -> itemOf(list, update.itemId()).forEach(item -> update(edit, item, update));
            case ShoppingOp.Buy buy -> itemOf(list, buy.itemId()).stream().filter(ShoppingItem::isActive)
                    .forEach(item -> takeOff(edit, item, ItemChange.BOUGHT, () -> item.buy(edit.at())));
            case ShoppingOp.Restore restore -> itemOf(list, restore.itemId()).stream().filter(item -> !item.isActive())
                    .forEach(item -> restore(edit, item, restore.spec()));
            case ShoppingOp.Delete delete -> itemOf(list, delete.itemId()).forEach(item -> delete(edit, item));
        }
    }

    /** Millisecond precision, so a recorded moment compares equal once stored. */
    private Instant momentOf(ShoppingOp op) {
        var now = clock.instant();
        var claimed = op.at();
        var trusted = claimed != null && !claimed.isAfter(now) && claimed.isAfter(now.minus(TRUSTED_DEVICE_PAST));
        return (trusted ? claimed : now).truncatedTo(ChronoUnit.MILLIS);
    }

    private void add(Edit edit, ShoppingOp.Add add) {
        var key = ShoppingNames.key(add.name());
        if (key.isEmpty()) {
            return;
        }
        var existing = itemRepository.findByListAndNameKeyAndDeletedFalse(edit.list(), key);
        if (existing.isPresent()) {
            var item = existing.get();
            var takesBack = item.wasJustBought(edit.at());
            stamp(edit.list(), item, () -> {
                if (item.isActive()) {
                    item.addMore(add.spec(), add.sources(), edit.at());
                } else {
                    bringBack(edit, item, add.spec(), add.sources());
                }
                if (add.prioritized()) {
                    item.setPrioritized(true);
                }
            });
            if (!takesBack) {
                recordChange(edit, item, ItemChange.ADDED, add.spec(), add.sources(), null);
            }
            return;
        }
        if (itemRepository.existsById(add.itemId())
                || itemRepository.countByListAndStatusAndDeletedFalse(edit.list(), ItemStatus.ACTIVE) >= MAX_ACTIVE_ITEMS) {
            return;
        }
        var placement = add.aisle() == null || add.aisle() == Aisle.OTHER
                ? placeTyped(add.name(), edit.actor())
                : new ItemPlacement(add.aisle(), add.icon());
        var item = ShoppingItem.builder()
                .id(add.itemId()).list(edit.list()).name(add.name().strip()).nameKey(key)
                .aisle(placement.aisle()).icon(placement.icon())
                .status(ItemStatus.ACTIVE).prioritized(add.prioritized()).addedBy(edit.actor())
                .addedAt(edit.at()).listedAt(edit.at())
                .sources(new ArrayList<>(add.sources()))
                .build();
        stamp(edit.list(), item, () -> item.replaceSpec(add.spec()));
        recordChange(edit, item, ItemChange.ADDED, add.spec(), add.sources(), null);
    }

    /** Recorded unless it takes back a slip of a tick. */
    private void restore(Edit edit, ShoppingItem item, String spec) {
        var takesBack = item.wasJustBought(edit.at());
        stamp(edit.list(), item, () -> bringBack(edit, item, spec, List.of()));
        if (!takesBack) {
            recordChange(edit, item, ItemChange.RESTORED);
        }
    }

    /** Brought back right after the tick, the tick was a slip and is taken back; that leaves no trace. */
    private void bringBack(Edit edit, ShoppingItem item, String spec, List<ItemSource> sources) {
        if (item.wasJustBought(edit.at())) {
            events.publishEvent(new ShoppingPurchaseTakenBack(edit.list(), item.getNameKey(), item.getBoughtAt()));
            item.takeBack(spec, sources, edit.at());
        } else {
            item.reactivate(spec, sources, edit.at());
        }
    }

    private void delete(Edit edit, ShoppingItem item) {
        if (item.isActive()) {
            takeOff(edit, item, ItemChange.REMOVED, item::delete);
        } else {
            stamp(edit.list(), item, item::delete);
        }
    }

    /** Off the list within a slip of going on it, nothing about it happened. */
    private void takeOff(Edit edit, ShoppingItem item, ItemChange kind, Runnable change) {
        stamp(edit.list(), item, change);
        if (item.wasJustListed(edit.at())) {
            events.publishEvent(new ShoppingItemSlipped(edit.list(), item.getId(), item.getListedAt()));
        } else {
            recordChange(edit, item, kind);
        }
    }

    /** Each field a person actually changed is recorded on its own. */
    private void update(Edit edit, ShoppingItem item, ShoppingOp.Update update) {
        var name = item.getName();
        var spec = item.getSpec();
        var aisle = item.getAisle();
        var prioritized = item.isPrioritized();
        stamp(edit.list(), item, () -> {
            if (update.name() != null && !update.name().isBlank()) {
                rename(edit, item, update.name());
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
        if (!item.getName().equals(name)) {
            recordChange(edit, item, ItemChange.RENAMED, null, List.of(), name);
        }
        if (!Objects.equals(item.getSpec(), spec)) {
            recordChange(edit, item, ItemChange.SPEC_CHANGED, item.getSpec(), List.of(), null);
        }
        if (item.getAisle() != aisle) {
            recordChange(edit, item, ItemChange.AISLE_CHANGED);
        }
        if (item.isPrioritized() != prioritized) {
            recordChange(edit, item, item.isPrioritized() ? ItemChange.PRIORITIZED : ItemChange.UNPRIORITIZED);
        }
    }

    /** Onto a name another item already has, the rename is dropped: the list holds each name once. */
    private void rename(Edit edit, ShoppingItem item, String name) {
        var key = ShoppingNames.key(name);
        if (!key.equals(item.getNameKey())
                && itemRepository.findByListAndNameKeyAndDeletedFalse(edit.list(), key).isPresent()) {
            return;
        }
        item.rename(name.strip(), key);
        if (!item.isAisleManual()) {
            var placement = placeTyped(name, edit.actor());
            item.place(placement.aisle(), placement.icon());
        }
    }

    private void recordChange(Edit edit, ShoppingItem item, ItemChange kind) {
        recordChange(edit, item, kind, null, List.of(), null);
    }

    private void recordChange(Edit edit, ShoppingItem item, ItemChange kind, String spec, List<ItemSource> sources,
            String previousName) {
        if (edit.actor() != null) {
            events.publishEvent(new ShoppingItemChanged(edit.list(), item, kind, edit.at(), edit.actor(), spec,
                    List.copyOf(sources), previousName));
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
