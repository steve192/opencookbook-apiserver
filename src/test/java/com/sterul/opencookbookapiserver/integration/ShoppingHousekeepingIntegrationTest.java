package com.sterul.opencookbookapiserver.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.sterul.opencookbookapiserver.entities.PlanScope;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.entities.catalogue.Aisle;
import com.sterul.opencookbookapiserver.entities.shopping.ItemStatus;
import com.sterul.opencookbookapiserver.entities.shopping.ShoppingItem;
import com.sterul.opencookbookapiserver.repositories.ShoppingItemRepository;
import com.sterul.opencookbookapiserver.repositories.ShoppingListRepository;
import com.sterul.opencookbookapiserver.repositories.UserRepository;
import com.sterul.opencookbookapiserver.services.shopping.ShoppingListService;
import com.sterul.opencookbookapiserver.services.shopping.sync.ShoppingHousekeeping;
import com.sterul.opencookbookapiserver.services.shopping.sync.ShoppingOp;
import com.sterul.opencookbookapiserver.services.shopping.sync.ShoppingSyncService;

/** Lists stay small, and a device that slept through a purge still ends up right. */
@SpringBootTest
@ActiveProfiles("integration-test")
class ShoppingHousekeepingIntegrationTest extends IntegrationTestBase {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ShoppingListRepository listRepository;
    @Autowired
    private ShoppingItemRepository itemRepository;
    @Autowired
    private ShoppingListService lists;
    @Autowired
    private ShoppingSyncService sync;
    @Autowired
    private ShoppingHousekeeping housekeeping;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private CookpalUser cook;
    private PlanScope scope;
    private Long listId;

    @BeforeEach
    void setup() {
        listRepository.deleteAll();
        cook = TestAccounts.ensure(userRepository, "housekeeping@example.invalid");
        scope = PlanScope.of(cook);
        listId = lists.defaultListIn(scope).getId();
    }

    @Test
    void onlyTheNewestPurchasesAreKept() {
        var ops = new ArrayList<ShoppingOp>();
        for (var index = 0; index < 62; index++) {
            var id = UUID.randomUUID().toString();
            ops.add(new ShoppingOp.Add(null, id, "Ding " + index, null, Aisle.OTHER, null, List.of(), false));
            ops.add(new ShoppingOp.Buy(null, id));
        }
        sync.apply(listId, scope, ops, 0, cook);

        housekeeping.trimRecentlyBought();

        var list = listRepository.findById(listId).orElseThrow();
        assertEquals(60, itemRepository.countByListAndStatusAndDeletedFalse(list, ItemStatus.BOUGHT));
    }

    @Test
    void aDeviceBehindThePurgeGetsTheWholeList() {
        var gone = UUID.randomUUID().toString();
        var kept = UUID.randomUUID().toString();
        var afterAdding = sync.apply(listId, scope, List.of(
                new ShoppingOp.Add(null, gone, "Brot", null, Aisle.OTHER, null, List.of(), false),
                new ShoppingOp.Add(null, kept, "Butter", null, Aisle.OTHER, null, List.of(), false)), 0, cook).version();
        sync.apply(listId, scope, List.of(new ShoppingOp.Delete(null, gone)), 0, cook);
        jdbcTemplate.update("UPDATE shopping_item SET last_change = now() - interval '31 days' WHERE id = ?", gone);

        housekeeping.purgeTombstones();

        assertTrue(itemRepository.findById(gone).isEmpty());
        var changes = sync.changesSince(listId, scope, afterAdding);
        assertTrue(changes.full());
        assertEquals(List.of(kept), changes.items().stream().map(ShoppingItem::getId).toList());
    }
}
