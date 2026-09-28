package com.sterul.opencookbookapiserver.repositories;

import java.time.Instant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sterul.opencookbookapiserver.entities.shopping.AppliedShoppingOp;

public interface AppliedShoppingOpRepository extends JpaRepository<AppliedShoppingOp, String> {

    @Modifying
    @Query("delete from AppliedShoppingOp op where op.appliedOn < :before")
    int deleteAppliedBefore(@Param("before") Instant before);
}
