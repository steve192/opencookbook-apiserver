package com.sterul.opencookbookapiserver.controllers;

import java.util.Date;
import java.util.List;
import java.util.NoSuchElementException;

import com.sterul.opencookbookapiserver.entities.WeekplanDay;
import com.sterul.opencookbookapiserver.services.WeekplanService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import io.swagger.v3.oas.annotations.parameters.RequestBody;

@Controller
@RequestMapping("/api/v1/weekplan")
public class WeekplanController extends BaseController {

    @Autowired
    WeekplanService weekplanService;

    @GetMapping("/{from}/to/{to}")
    public List<WeekplanDay> getBetweenDates(@PathVariable @DateTimeFormat(pattern = "yyyy-MM-dd") Date from,
            @PathVariable @DateTimeFormat(pattern = "yyyy-MM-dd") Date to) {
        return weekplanService.getWeekplanDaysBetweenTime(from, to, getLoggedInUser());
    }

    @PutMapping("/{date}")
    public WeekplanDay createAndUpdate(@PathVariable @DateTimeFormat(pattern = "yyy-MM-dd") Date date,
            @RequestBody WeekplanDay weekplanDayPut) {

        WeekplanDay changedWeekplanDay;
        try {
            var existingWeekplanDay = weekplanService.getWeekplanDayByDate(date, getLoggedInUser());
            weekplanDayPut.setId(existingWeekplanDay.getId());
            weekplanDayPut.setDay(date);
            weekplanDayPut.setOwner(existingWeekplanDay.getOwner());
            changedWeekplanDay = weekplanService.updateWeekplanDay(weekplanDayPut);
        } catch (NoSuchElementException e) {
            weekplanDayPut.setOwner(getLoggedInUser());
            weekplanDayPut.setDay(date);
            changedWeekplanDay = weekplanService.createWeekplanDay(weekplanDayPut);
        }

        return changedWeekplanDay;
    }

}
