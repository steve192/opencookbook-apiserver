package com.sterul.opencookbookapiserver.controllers;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.NoSuchElementException;

import com.sterul.opencookbookapiserver.controllers.exceptions.NotAuthorizedException;
import com.sterul.opencookbookapiserver.controllers.requests.WeekplanDayPut;
import com.sterul.opencookbookapiserver.entities.WeekplanDay;
import com.sterul.opencookbookapiserver.services.RecipeService;
import com.sterul.opencookbookapiserver.services.WeekplanService;
import com.sterul.opencookbookapiserver.services.exceptions.ElementNotFound;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/api/v1/weekplan")
public class WeekplanController extends BaseController {

    @Autowired
    WeekplanService weekplanService;
    @Autowired
    RecipeService recipeService;

    @GetMapping("/{from}/to/{to}")
    public List<WeekplanDay> getBetweenDates(@PathVariable @DateTimeFormat(pattern = "yyyy-MM-dd") Date from,
            @PathVariable @DateTimeFormat(pattern = "yyyy-MM-dd") Date to) {
        return weekplanService.getWeekplanDaysBetweenTime(from, to, getLoggedInUser());
    }

    @PutMapping("/{date}")
    public WeekplanDay createAndUpdate(@PathVariable @DateTimeFormat(pattern = "yyy-MM-dd") Date date,
            @RequestBody WeekplanDayPut weekplanDayPut) throws NotAuthorizedException, ElementNotFound {

        WeekplanDay changedWeekplanDay;
        try {
            final var existingWeekplanDay = weekplanService.getWeekplanDayByDate(date, getLoggedInUser());
            populateWeekplanDayWithRecipes(weekplanDayPut, existingWeekplanDay);

            changedWeekplanDay = weekplanService.updateWeekplanDay(existingWeekplanDay);
        } catch (NoSuchElementException e) {
            final var newWeekplanDay = new WeekplanDay();
            newWeekplanDay.setRecipes(new ArrayList<>());
            newWeekplanDay.setOwner(getLoggedInUser());
            newWeekplanDay.setDay(date);
            populateWeekplanDayWithRecipes(weekplanDayPut, newWeekplanDay);

            changedWeekplanDay = weekplanService.createWeekplanDay(newWeekplanDay);
        }

        return changedWeekplanDay;
    }

    private void populateWeekplanDayWithRecipes(WeekplanDayPut weekplanDayPut, final WeekplanDay newWeekplanDay)
            throws NotAuthorizedException, ElementNotFound {
                
        newWeekplanDay.getRecipes().clear();
        for (Long recipeId : weekplanDayPut.getRecipeIds()) {
            if (!recipeService.hasAccessPermissionToRecipe(recipeId, getLoggedInUser())) {
                throw new NotAuthorizedException();
            }
            var recipe = recipeService.getRecipeById(recipeId);
            newWeekplanDay.getRecipes().add(recipe);
        }
    }

}
