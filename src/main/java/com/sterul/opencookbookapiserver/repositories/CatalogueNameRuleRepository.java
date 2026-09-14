package com.sterul.opencookbookapiserver.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sterul.opencookbookapiserver.entities.nutrition.CatalogueFood;
import com.sterul.opencookbookapiserver.entities.nutrition.CatalogueNameRule;

public interface CatalogueNameRuleRepository extends JpaRepository<CatalogueNameRule, Long> {

    List<CatalogueNameRule> findAllByName(String name);

    List<CatalogueNameRule> findAllByOrderByNameAsc();

    /** Rules the target already has stay with the source, and go when it is deleted. */
    @Modifying
    @Query("update CatalogueNameRule rule set rule.catalogueFood = :target where rule.catalogueFood = :source "
            + "and not exists (select other from CatalogueNameRule other "
            + "where other.catalogueFood = :target and other.name = rule.name and other.kind = rule.kind)")
    int moveToFood(@Param("source") CatalogueFood source, @Param("target") CatalogueFood target);
}
