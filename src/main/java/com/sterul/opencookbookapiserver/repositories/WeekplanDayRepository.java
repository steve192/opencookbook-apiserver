package com.sterul.opencookbookapiserver.repositories;

import java.util.Date;
import java.util.List;

import com.sterul.opencookbookapiserver.entities.WeekplanDay;
import com.sterul.opencookbookapiserver.entities.account.User;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WeekplanDayRepository extends JpaRepository<WeekplanDay, Long> {
    public List<WeekplanDay> findAllByDayBetweenAndOwner(Date dayStart, Date dayEnd, User owner);
}
