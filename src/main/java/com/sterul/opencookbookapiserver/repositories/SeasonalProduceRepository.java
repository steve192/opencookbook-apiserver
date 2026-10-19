package com.sterul.opencookbookapiserver.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.sterul.opencookbookapiserver.entities.seasonal.SeasonalProduce;

public interface SeasonalProduceRepository extends JpaRepository<SeasonalProduce, Long> {

    @EntityGraph(attributePaths = {"food", "food.names", "months"})
    List<SeasonalProduce> findDistinctByMonthsRegion(String region);
}
