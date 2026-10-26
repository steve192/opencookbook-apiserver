package com.sterul.opencookbookapiserver.services.shopping.history;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sterul.opencookbookapiserver.entities.PlanScope;
import com.sterul.opencookbookapiserver.entities.shopping.ItemChange;
import com.sterul.opencookbookapiserver.entities.shopping.ShoppingList;
import com.sterul.opencookbookapiserver.entities.shopping.ShoppingListEvent;
import com.sterul.opencookbookapiserver.repositories.ShoppingListEventRepository;
import com.sterul.opencookbookapiserver.services.shopping.ShoppingListService;
import com.sterul.opencookbookapiserver.services.shopping.sync.ShoppingItemChanged;

import lombok.extern.slf4j.Slf4j;

/** What people changed on each list and when: shown as its history, and what its earlier entries and regular items come from. */
@Service
@Slf4j
@Transactional
public class ShoppingHistory {

    /** Enough for six trips even when some purchases fell on the same day. */
    static final int PURCHASES_CONSIDERED = 20;
    /** A name not put on the list for this long is no longer offered while typing. */
    static final Duration ENTRIES_REMEMBERED = Duration.ofDays(183);
    static final int ENTRIES_OFFERED = 500;
    static final int EVENTS_PER_PAGE = 50;
    /** Three purchases of the rarest rhythm a list learns. */
    static final Duration KEPT = Duration.ofDays(365);

    private final ShoppingListEventRepository repository;
    private final ShoppingListService lists;
    private final Clock clock;

    public ShoppingHistory(ShoppingListEventRepository repository, ShoppingListService lists, Clock clock) {
        this.repository = repository;
        this.lists = lists;
        this.clock = clock;
    }

    void remember(ShoppingItemChanged changed) {
        var item = changed.item();
        repository.save(ShoppingListEvent.builder()
                .list(changed.list()).itemId(item.getId()).at(changed.at()).kind(changed.kind()).actor(changed.actor())
                .nameKey(item.getNameKey()).name(item.getName()).aisle(item.getAisle()).icon(item.getIcon())
                .spec(changed.spec()).previousName(changed.previousName())
                .sources(new ArrayList<>(changed.sources()))
                .build());
    }

    /** A slip leaves no trace: what happened to the item since it was put on the list is forgotten. */
    void forgetSince(ShoppingList list, String itemId, Instant listedAt) {
        repository.deleteSince(list, itemId, listedAt);
    }

    /** A purchase is only recorded after more than a slip on the list, so there may be nothing to take back. */
    void takeBack(ShoppingList list, String nameKey, Instant boughtAt) {
        repository.deleteEvent(list, nameKey, ItemChange.BOUGHT, boughtAt);
    }

    /** Newest first. */
    List<Instant> purchases(ShoppingList list, String nameKey) {
        return repository.findTimes(list, nameKey, ItemChange.BOUGHT, Limit.of(PURCHASES_CONSIDERED));
    }

    /** The names put on a list lately, offered again while typing. */
    @Transactional(readOnly = true)
    public List<ShoppingListEvent> entriesOf(Long listId, PlanScope scope) {
        return repository.findLatestPerNameSince(lists.listIn(listId, scope), ItemChange.ADDED,
                clock.instant().minus(ENTRIES_REMEMBERED), Limit.of(ENTRIES_OFFERED));
    }

    /** Newest first. */
    @Transactional(readOnly = true)
    public Slice<ShoppingListEvent> historyOf(Long listId, PlanScope scope, int page) {
        return repository.findAllByListOrderByAtDescIdDesc(lists.listIn(listId, scope),
                PageRequest.of(page, EVENTS_PER_PAGE));
    }

    public void forgetOld() {
        var forgotten = repository.deleteBefore(clock.instant().minus(KEPT));
        log.info("Forgot {} shopping list events older than a year", forgotten);
    }
}
