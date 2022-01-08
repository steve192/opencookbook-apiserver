package com.sterul.opencookbookapiserver.services;

import java.util.Date;
import java.util.List;
import java.util.NoSuchElementException;

import com.sterul.opencookbookapiserver.entities.WeekplanDay;
import com.sterul.opencookbookapiserver.entities.account.User;
import com.sterul.opencookbookapiserver.repositories.WeekplanDayRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class WeekplanService {

    @Autowired
    WeekplanDayRepository weekplanDayRepository;

    public List<WeekplanDay> getWeekplanDaysBetweenTime(Date startTime, Date endTime, User owner) {
        return weekplanDayRepository.findAllByDayBetweenAndOwner(startTime, endTime, owner);
    }

    public WeekplanDay getWeekplanDayByDate(Date date, User owner) throws NoSuchElementException {
        var weekplanDay = weekplanDayRepository.findSingleByDayAndOwner(date, owner);
        if (weekplanDay == null) {
            throw new NoSuchElementException();
        }
        return weekplanDay;
    }

    public WeekplanDay createWeekplanDay(WeekplanDay weekplanDay) {
        return weekplanDayRepository.save(weekplanDay);
    }

    public WeekplanDay updateWeekplanDay(WeekplanDay weekplanDay) {
        return weekplanDayRepository.save(weekplanDay);
    }

    public List<WeekplanDay> getWeekplanDaysByRecipe(Long id) {
        return weekplanDayRepository.findAllByRecipes_Id(id);
    }

    public List<WeekplanDay> getWeekplanDaysByOwner(User user) {
        return weekplanDayRepository.findAllByOwner(user);
    }

    public void deleteWeekplanDay(Long id) {
        weekplanDayRepository.deleteById(id);
    }

}
