package com.sterul.opencookbookapiserver.services;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.sterul.opencookbookapiserver.entities.WeekplanDay;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.repositories.WeekplanDayRepository;

import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@Transactional
public class WeekplanService {

    @Autowired
    WeekplanDayRepository weekplanDayRepository;

    public List<WeekplanDay> getWeekplanDaysBetweenTime(LocalDate startTime, LocalDate endTime, CookpalUser owner) {
        return weekplanDayRepository.findAllByPlanDateBetweenAndOwner(startTime, endTime, owner);
    }

    /** The stored day, or a new empty one that is saved only once something is planned on it. */
    public WeekplanDay dayOf(LocalDate date, CookpalUser owner) {
        return Optional.ofNullable(weekplanDayRepository.findSingleByPlanDateAndOwner(date, owner)).orElseGet(() -> {
            var day = new WeekplanDay();
            day.setOwner(owner);
            day.setPlanDate(date);
            day.setRecipes(new ArrayList<>());
            return day;
        });
    }

    public WeekplanDay updateWeekplanDay(WeekplanDay weekplanDay) {
        log.info("Saving weekplan day {} of user {}", weekplanDay.getPlanDate(), weekplanDay.getOwner());
        return weekplanDayRepository.save(weekplanDay);
    }

    public List<WeekplanDay> getWeekplanDaysByRecipe(Long id) {
        return weekplanDayRepository.findAllByRecipes_Recipe_Id(id);
    }

    public List<WeekplanDay> getWeekplanDaysByOwner(CookpalUser user) {
        return weekplanDayRepository.findAllByOwner(user);
    }

    public void deleteWeekplanDay(Long id) {
        log.info("Deleting weekplan day {}", id);
        weekplanDayRepository.deleteById(id);
    }

}
