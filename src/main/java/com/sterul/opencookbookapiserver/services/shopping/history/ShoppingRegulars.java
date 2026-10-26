package com.sterul.opencookbookapiserver.services.shopping.history;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sterul.opencookbookapiserver.entities.PlanScope;
import com.sterul.opencookbookapiserver.entities.shopping.ShoppingItem;
import com.sterul.opencookbookapiserver.entities.shopping.ShoppingList;
import com.sterul.opencookbookapiserver.entities.shopping.ShoppingRegular;
import com.sterul.opencookbookapiserver.repositories.ShoppingRegularRepository;
import com.sterul.opencookbookapiserver.services.exceptions.ElementNotFound;
import com.sterul.opencookbookapiserver.services.shopping.ShoppingListService;

/** The names each list buys at a rhythm, learned from its purchases. */
@Service
@Transactional
public class ShoppingRegulars {

    private final ShoppingRegularRepository repository;
    private final ShoppingListService lists;
    private final Clock clock;

    public ShoppingRegulars(ShoppingRegularRepository repository, ShoppingListService lists, Clock clock) {
        this.repository = repository;
        this.lists = lists;
        this.clock = clock;
    }

    /** @param purchases the name's, the one just bought included */
    void bought(ShoppingList list, ShoppingItem item, List<Instant> purchases) {
        var regular = repository.findByListAndNameKey(list, item.getNameKey())
                .orElseGet(() -> ShoppingRegular.builder().list(list).nameKey(item.getNameKey()).build());
        regular.lastSeenAs(item.getName(), item.getAisle(), item.getIcon());
        regular.learn(purchases);
        repository.save(regular);
    }

    /** @param purchases what is left of the name's */
    void purchaseTakenBack(ShoppingList list, String nameKey, List<Instant> purchases) {
        repository.findByListAndNameKey(list, nameKey).ifPresent(regular -> {
            if (purchases.isEmpty() && !regular.isMuted()) {
                repository.delete(regular);
            } else {
                regular.learn(purchases);
            }
        });
    }

    @Transactional(readOnly = true)
    public List<ShoppingRegular> regularsOf(Long listId, PlanScope scope) {
        return repository.findShownIn(lists.listIn(listId, scope));
    }

    public ShoppingRegular mute(Long listId, PlanScope scope, Long regularId, boolean muted) {
        var regular = lockedIn(listId, scope, regularId);
        regular.mute(muted);
        return repository.save(regular);
    }

    public ShoppingRegular notNow(Long listId, PlanScope scope, Long regularId) {
        var regular = lockedIn(listId, scope, regularId);
        regular.notNow(clock.instant());
        return repository.save(regular);
    }

    /** Under the list's lock, like the purchases it learns from. */
    private ShoppingRegular lockedIn(Long listId, PlanScope scope, Long regularId) {
        return repository.findByIdAndList(regularId, lists.lockedIn(listId, scope)).orElseThrow(ElementNotFound::new);
    }

    /** Names the list stopped buying a year ago; a muted one stays, so it is never suggested again. */
    public void forgetOld() {
        repository.deleteUnmutedBoughtBefore(clock.instant().minus(ShoppingHistory.KEPT));
    }
}
