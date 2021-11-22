package com.sterul.opencookbookapiserver.services;

import java.util.Date;
import java.util.List;

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
}
