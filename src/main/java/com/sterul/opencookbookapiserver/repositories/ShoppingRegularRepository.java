package com.sterul.opencookbookapiserver.repositories;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sterul.opencookbookapiserver.entities.shopping.ShoppingList;
import com.sterul.opencookbookapiserver.entities.shopping.ShoppingRegular;

public interface ShoppingRegularRepository extends JpaRepository<ShoppingRegular, Long> {

    Optional<ShoppingRegular> findByListAndNameKey(ShoppingList list, String nameKey);

    Optional<ShoppingRegular> findByIdAndList(Long id, ShoppingList list);

    /** What settings show: names with a rhythm, and those somebody muted. */
    @Query("select regular from ShoppingRegular regular where regular.list = :list "
            + "and (regular.rhythmSeconds is not null or regular.muted = true) order by regular.name")
    List<ShoppingRegular> findShownIn(@Param("list") ShoppingList list);

    @Modifying
    @Query("delete from ShoppingRegular regular where regular.muted = false "
            + "and (regular.lastBoughtAt is null or regular.lastBoughtAt < :before)")
    int deleteUnmutedBoughtBefore(@Param("before") Instant before);
}
