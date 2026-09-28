package com.sterul.opencookbookapiserver.repositories;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sterul.opencookbookapiserver.entities.shopping.ItemStatus;
import com.sterul.opencookbookapiserver.entities.shopping.ShoppingItem;
import com.sterul.opencookbookapiserver.entities.shopping.ShoppingList;

public interface ShoppingItemRepository extends JpaRepository<ShoppingItem, String> {

    Optional<ShoppingItem> findByListAndNameKeyAndDeletedFalse(ShoppingList list, String nameKey);

    Optional<ShoppingItem> findByIdAndListAndDeletedFalse(String id, ShoppingList list);

    List<ShoppingItem> findAllByListAndDeletedFalse(ShoppingList list);

    /** Tombstones included: they are how a device learns of a delete. */
    List<ShoppingItem> findAllByListAndVersionGreaterThan(ShoppingList list, long version);

    long countByListAndStatusAndDeletedFalse(ShoppingList list, ItemStatus status);

    List<ShoppingItem> findAllByListAndStatusAndDeletedFalseOrderByBoughtAtDesc(ShoppingList list, ItemStatus status);

    @Query("select item.list from ShoppingItem item where item.status = :status and item.deleted = false "
            + "group by item.list having count(item) > :limit")
    List<ShoppingList> findListsWithMoreThan(@Param("status") ItemStatus status, @Param("limit") long limit);

    @Query("select item.list.id as listId, max(item.version) as version from ShoppingItem item "
            + "where item.deleted = true and item.lastChange < :before group by item.list.id")
    List<TombstoneHorizon> findTombstoneHorizons(@Param("before") Instant before);

    @Modifying
    @Query("delete from ShoppingItem item where item.list.id = :listId and item.deleted = true "
            + "and item.version <= :version")
    int deleteTombstones(@Param("listId") Long listId, @Param("version") long version);

    interface TombstoneHorizon {
        Long getListId();

        long getVersion();
    }
}
