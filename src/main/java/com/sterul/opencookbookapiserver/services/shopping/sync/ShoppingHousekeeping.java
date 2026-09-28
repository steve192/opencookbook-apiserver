package com.sterul.opencookbookapiserver.services.shopping.sync;

import java.time.Clock;
import java.time.Duration;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sterul.opencookbookapiserver.entities.shopping.ItemStatus;
import com.sterul.opencookbookapiserver.repositories.AppliedShoppingOpRepository;
import com.sterul.opencookbookapiserver.repositories.ShoppingItemRepository;
import com.sterul.opencookbookapiserver.repositories.ShoppingListRepository;

import lombok.extern.slf4j.Slf4j;

/** Keeps lists from growing for ever: old purchases, delete markers and applied op ids. */
@Service
@Slf4j
@Transactional
public class ShoppingHousekeeping {

    static final int RECENTLY_BOUGHT_KEPT = 60;
    /** Longer than a phone is plausibly offline; one that was longer gets the whole list again. */
    static final Duration TOMBSTONE_LIFETIME = Duration.ofDays(30);
    /** Longer than a device keeps retrying the same batch. */
    static final Duration APPLIED_OP_LIFETIME = Duration.ofDays(7);

    private final ShoppingListRepository listRepository;
    private final ShoppingItemRepository itemRepository;
    private final AppliedShoppingOpRepository appliedOpRepository;
    private final ShoppingSyncService sync;
    private final Clock clock;

    public ShoppingHousekeeping(ShoppingListRepository listRepository, ShoppingItemRepository itemRepository,
            AppliedShoppingOpRepository appliedOpRepository, ShoppingSyncService sync, Clock clock) {
        this.listRepository = listRepository;
        this.itemRepository = itemRepository;
        this.appliedOpRepository = appliedOpRepository;
        this.sync = sync;
        this.clock = clock;
    }

    /** Deleted like any other item, so devices hear of it. */
    public void trimRecentlyBought() {
        for (var crowded : itemRepository.findListsWithMoreThan(ItemStatus.BOUGHT, RECENTLY_BOUGHT_KEPT)) {
            var list = listRepository.findLockedById(crowded.getId()).orElseThrow();
            var surplus = itemRepository.findAllByListAndStatusAndDeletedFalseOrderByBoughtAtDesc(list, ItemStatus.BOUGHT)
                    .stream().skip(RECENTLY_BOUGHT_KEPT)
                    .<ShoppingOp>map(item -> new ShoppingOp.Delete(null, item.getId())).toList();
            sync.applyTo(list, surplus, null);
        }
    }

    public void purgeTombstones() {
        for (var horizon : itemRepository.findTombstoneHorizons(clock.instant().minus(TOMBSTONE_LIFETIME))) {
            var list = listRepository.findLockedById(horizon.getListId()).orElseThrow();
            var purged = itemRepository.deleteTombstones(list.getId(), horizon.getVersion());
            list.setPurgedVersion(Math.max(list.getPurgedVersion(), horizon.getVersion()));
            listRepository.save(list);
            log.info("Purged {} deleted items of shopping list {}", purged, list.getId());
        }
    }

    public void forgetAppliedOps() {
        appliedOpRepository.deleteAppliedBefore(clock.instant().minus(APPLIED_OP_LIFETIME));
    }
}
