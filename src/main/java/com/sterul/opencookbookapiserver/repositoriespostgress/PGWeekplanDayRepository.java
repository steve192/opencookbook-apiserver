package com.sterul.opencookbookapiserver.repositoriespostgress;

import java.util.Date;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sterul.opencookbookapiserver.repositoriespostgress.entities.WeekplanDay;
import com.sterul.opencookbookapiserver.repositoriespostgress.entities.account.CookpalUser;

public interface PGWeekplanDayRepository extends JpaRepository<WeekplanDay, Long> {
    List<WeekplanDay> findAllByDayBetweenAndOwner(Date dayStart, Date dayEnd, CookpalUser owner);

    WeekplanDay findSingleByDayAndOwner(Date day, CookpalUser owner);

    List<WeekplanDay> findAllByRecipes_Recipe_Id(Long recipeId);

    List<WeekplanDay> findAllByOwner(CookpalUser owner);
}
