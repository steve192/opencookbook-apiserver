package com.sterul.opencookbookapiserver.repositories;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sterul.opencookbookapiserver.entities.shopping.ItemChange;
import com.sterul.opencookbookapiserver.entities.shopping.ShoppingList;
import com.sterul.opencookbookapiserver.entities.shopping.ShoppingListEvent;

public interface ShoppingListEventRepository extends JpaRepository<ShoppingListEvent, Long> {

    @EntityGraph(attributePaths = "actor")
    Slice<ShoppingListEvent> findAllByListOrderByAtDescIdDesc(ShoppingList list, Pageable pageable);

    @Query("select event.at from ShoppingListEvent event where event.list = :list and event.nameKey = :nameKey "
            + "and event.kind = :kind order by event.at desc")
    List<Instant> findTimes(@Param("list") ShoppingList list, @Param("nameKey") String nameKey,
            @Param("kind") ItemChange kind, Limit limit);

    @Modifying
    @Query("delete from ShoppingListEvent event where event.list = :list and event.nameKey = :nameKey "
            + "and event.kind = :kind and event.at = :at")
    void deleteEvent(@Param("list") ShoppingList list, @Param("nameKey") String nameKey,
            @Param("kind") ItemChange kind, @Param("at") Instant at);

    @Modifying
    @Query("delete from ShoppingListEvent event where event.list = :list and event.itemId = :itemId "
            + "and event.at >= :since")
    void deleteSince(@Param("list") ShoppingList list, @Param("itemId") String itemId, @Param("since") Instant since);

    /** Each name's latest event of that kind since then, newest first. */
    @Query("select event from ShoppingListEvent event where event.list = :list and event.kind = :kind "
            + "and event.at >= :since and event.at = (select max(other.at) from ShoppingListEvent other "
            + "where other.list = event.list and other.nameKey = event.nameKey and other.kind = :kind) "
            + "order by event.at desc")
    List<ShoppingListEvent> findLatestPerNameSince(@Param("list") ShoppingList list, @Param("kind") ItemChange kind,
            @Param("since") Instant since, Limit limit);

    @Modifying
    @Query("delete from ShoppingListEvent event where event.at < :before")
    int deleteBefore(@Param("before") Instant before);
}
