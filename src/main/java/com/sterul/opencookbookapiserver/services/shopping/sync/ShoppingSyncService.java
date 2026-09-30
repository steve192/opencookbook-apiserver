package com.sterul.opencookbookapiserver.services.shopping.sync;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sterul.opencookbookapiserver.entities.PlanScope;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.entities.shopping.AppliedShoppingOp;
import com.sterul.opencookbookapiserver.entities.shopping.ShoppingList;
import com.sterul.opencookbookapiserver.repositories.AppliedShoppingOpRepository;
import com.sterul.opencookbookapiserver.repositories.ShoppingItemRepository;
import com.sterul.opencookbookapiserver.repositories.ShoppingListRepository;
import com.sterul.opencookbookapiserver.services.exceptions.ElementNotFound;
import com.sterul.opencookbookapiserver.services.shopping.ShoppingListService;

/**
 * Keeps devices in step with a list. Every item change takes the list's next version, so a device
 * asks for what changed since the version it has; writers of one list are serialised by a row lock.
 */
@Service
@Transactional
public class ShoppingSyncService {

    private final ShoppingListRepository listRepository;
    private final ShoppingItemRepository itemRepository;
    private final AppliedShoppingOpRepository appliedOpRepository;
    private final ShoppingListService lists;
    private final ItemEditor editor;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public ShoppingSyncService(ShoppingListRepository listRepository, ShoppingItemRepository itemRepository,
            AppliedShoppingOpRepository appliedOpRepository, ShoppingListService lists, ItemEditor editor,
            ApplicationEventPublisher events, Clock clock) {
        this.listRepository = listRepository;
        this.itemRepository = itemRepository;
        this.appliedOpRepository = appliedOpRepository;
        this.lists = lists;
        this.editor = editor;
        this.events = events;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ItemChanges changesSince(Long listId, PlanScope scope, long since) {
        return changesOf(lists.listIn(listId, scope), since);
    }

    /** @return what changed since {@code since}, the device's own ops included */
    public ItemChanges apply(Long listId, PlanScope scope, List<ShoppingOp> ops, long since, CookpalUser actor) {
        return changesOf(applyOps(listId, scope, ops, actor), since);
    }

    /** Lines finished in the app's import sheet, each added as if typed. */
    public ShoppingList importLines(Long listId, PlanScope scope, List<ShoppingOp.Add> lines, CookpalUser actor) {
        return applyOps(listId, scope, lines.stream().<ShoppingOp>map(ShoppingSyncService::withNewItemId).toList(), actor);
    }

    private ShoppingList applyOps(Long listId, PlanScope scope, List<ShoppingOp> ops, CookpalUser actor) {
        return applyTo(listRepository.lockIn(listId, scope).orElseThrow(ElementNotFound::new), ops, actor);
    }

    /**
     * @param list locked by the caller
     * @param actor null for the server's own ops, which never add
     */
    ShoppingList applyTo(ShoppingList list, List<ShoppingOp> ops, CookpalUser actor) {
        var before = list.getVersion();
        for (var op : ops) {
            if (op.opId() != null && appliedOpRepository.existsById(op.opId())) {
                continue;
            }
            editor.apply(list, op, actor);
            if (op.opId() != null) {
                appliedOpRepository.save(new AppliedShoppingOp(op.opId(), list.getId(), clock.instant()));
            }
        }
        if (list.getVersion() != before) {
            listRepository.save(list);
            events.publishEvent(new ShoppingListChanged(list.getId(), list.getVersion()));
        }
        return list;
    }

    private static ShoppingOp.Add withNewItemId(ShoppingOp.Add line) {
        return new ShoppingOp.Add(null, UUID.randomUUID().toString(), line.name(), line.spec(), line.aisle(),
                line.icon(), line.sources(), line.prioritized());
    }

    /** A device that never synced, or last synced before tombstones it missed were purged, starts over. */
    private ItemChanges changesOf(ShoppingList list, long since) {
        var full = since <= 0 || since < list.getPurgedVersion() || since > list.getVersion();
        var items = full
                ? itemRepository.findAllByListAndDeletedFalse(list)
                : itemRepository.findAllByListAndVersionGreaterThan(list, since);
        return new ItemChanges(list.getVersion(), full, items);
    }
}
