package com.sterul.opencookbookapiserver.repositories;

import java.util.Date;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sterul.opencookbookapiserver.repositories.entities.WeekplanDay;
import com.sterul.opencookbookapiserver.repositories.entities.account.User;

public interface WeekplanDayRepository extends JpaRepository<WeekplanDay, Long> {
    List<WeekplanDay> findAllByDayBetweenAndOwner(Date dayStart, Date dayEnd, User owner);

    WeekplanDay findSingleByDayAndOwner(Date day, User owner);

    List<WeekplanDay> findAllByRecipes_Recipe_Id(Long recipeId);

    List<WeekplanDay> findAllByOwner(User owner);
}
