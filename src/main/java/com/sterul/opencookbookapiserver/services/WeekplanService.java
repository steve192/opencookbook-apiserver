package com.sterul.opencookbookapiserver.services;

import java.util.Date;
import java.util.List;
import java.util.NoSuchElementException;

import jakarta.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.sterul.opencookbookapiserver.entities.WeekplanDay;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.repositories.WeekplanDayRepository;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@Transactional
public class WeekplanService {

    @Autowired
    WeekplanDayRepository weekplanDayRepository;

    public List<WeekplanDay> getWeekplanDaysBetweenTime(Date startTime, Date endTime, CookpalUser owner) {
        return weekplanDayRepository.findAllByDayBetweenAndOwner(startTime, endTime, owner);
    }

    public WeekplanDay getWeekplanDayByDate(Date date, CookpalUser owner) throws NoSuchElementException {
        var weekplanDay = weekplanDayRepository.findSingleByDayAndOwner(date, owner);
        if (weekplanDay == null) {
            throw new NoSuchElementException();
        }
        return weekplanDay;
    }

    public WeekplanDay createWeekplanDay(WeekplanDay weekplanDay) {
        log.info("Creating weekplan day {} of user", weekplanDay.getDay(), weekplanDay.getOwner());
        return weekplanDayRepository.save(weekplanDay);
    }

    public WeekplanDay updateWeekplanDay(WeekplanDay weekplanDay) {
        log.info("Updating weekplan day {} of user {}", weekplanDay.getDay(), weekplanDay.getOwner());
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
