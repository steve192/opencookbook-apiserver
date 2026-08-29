package com.sterul.opencookbookapiserver.repositories;

import com.sterul.opencookbookapiserver.entities.WeekplanDay;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface WeekplanDayRepository extends JpaRepository<WeekplanDay, Long> {
    List<WeekplanDay> findAllByPlanDateBetweenAndOwner(LocalDate dayStart, LocalDate dayEnd, CookpalUser owner);

    WeekplanDay findSingleByPlanDateAndOwner(LocalDate day, CookpalUser owner);

    List<WeekplanDay> findAllByRecipes_Recipe_Id(Long recipeId);

    List<WeekplanDay> findAllByOwner(CookpalUser owner);
}
