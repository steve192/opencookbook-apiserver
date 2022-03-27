package com.sterul.opencookbookapiserver.controllers;

import com.sterul.opencookbookapiserver.controllers.exceptions.NotAuthorizedException;
import com.sterul.opencookbookapiserver.controllers.requests.WeekplanDayPut;
import com.sterul.opencookbookapiserver.controllers.responses.WeekplanDayResponse;
import com.sterul.opencookbookapiserver.entities.WeekplanDay;
import com.sterul.opencookbookapiserver.entities.WeekplanDayRecipe;
import com.sterul.opencookbookapiserver.services.RecipeService;
import com.sterul.opencookbookapiserver.services.WeekplanService;
import com.sterul.opencookbookapiserver.services.exceptions.ElementNotFound;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.NoSuchElementException;

@Controller
@RequestMapping("/api/v1/weekplan")
@Tag(name = "Weekplan", description = "Managing and fetching weekplans")
public class WeekplanController extends BaseController {

    @Autowired
    WeekplanService weekplanService;
    @Autowired
    RecipeService recipeService;

    @Operation(summary = "Fetch weekplan days in timerange")
    @GetMapping("/{from}/to/{to}")
    public List<WeekplanDayResponse> getBetweenDates(@PathVariable @DateTimeFormat(pattern = "yyyy-MM-dd") Date from,
                                                     @PathVariable @DateTimeFormat(pattern = "yyyy-MM-dd") Date to) {
        return weekplanService.getWeekplanDaysBetweenTime(from, to, getLoggedInUser()).stream()
                .map(this::entityToResponse).toList();
    }

    @Operation(summary = "Change a single weekplan day")
    @PutMapping("/{date}")
    public WeekplanDayResponse createAndUpdate(@PathVariable @DateTimeFormat(pattern = "yyy-MM-dd") Date date,
                                               @RequestBody WeekplanDayPut weekplanDayPut) throws NotAuthorizedException, ElementNotFound {

        WeekplanDay weekplanDayEntity;
        try {
            weekplanDayEntity = weekplanService.getWeekplanDayByDate(date, getLoggedInUser());
            populateWeekplanDayWithRecipes(weekplanDayPut, weekplanDayEntity);
            weekplanDayEntity = weekplanService.updateWeekplanDay(weekplanDayEntity);
        } catch (NoSuchElementException e) {
            weekplanDayEntity = new WeekplanDay();
            weekplanDayEntity.setRecipes(new ArrayList<>());
            weekplanDayEntity.setOwner(getLoggedInUser());
            weekplanDayEntity.setDay(date);
            populateWeekplanDayWithRecipes(weekplanDayPut, weekplanDayEntity);

            weekplanDayEntity = weekplanService.createWeekplanDay(weekplanDayEntity);
        }

        return entityToResponse(weekplanDayEntity);
    }

    private WeekplanDayResponse entityToResponse(WeekplanDay weekplanDayEntity) {
        var response = new WeekplanDayResponse();
        response.setDay(weekplanDayEntity.getDay());
        for (var recipe : weekplanDayEntity.getRecipes()) {
            if (recipe.isSimpleRecipe()) {
                var simpleRecipe = new WeekplanDayResponse.SimpleRecipe();
                simpleRecipe.setId(recipe.getId());
                simpleRecipe.setTitle(recipe.getSimpleRecipeText());
                response.getRecipes().add(simpleRecipe);
            } else {
                if (recipe.getRecipe() == null) {
                    // Something is wrong with the data, ignore this entry
                    continue;
                }
                var normalRecipe = new WeekplanDayResponse.NormalRecipe();
                normalRecipe.setId(recipe.getRecipe().getId());
                normalRecipe.setTitle(recipe.getRecipe().getTitle());
                if (!recipe.getRecipe().getImages().isEmpty()) {
                    normalRecipe.setTitleImageUuid(recipe.getRecipe().getImages().get(0).getUuid());
                }
                response.getRecipes().add(normalRecipe);
            }
        }
        return response;
    }

    private void populateWeekplanDayWithRecipes(WeekplanDayPut weekplanDayPut, final WeekplanDay newWeekplanDay)
            throws NotAuthorizedException, ElementNotFound {

        newWeekplanDay.getRecipes().clear();

        for (var recipe : weekplanDayPut.getRecipes()) {
            switch (recipe.getType()) {
                case NORMAL_RECIPE -> {
                    var recipeId = ((WeekplanDayPut.NormalRecipe) recipe).getId();

                    if (!recipeService.hasAccessPermissionToRecipe(recipeId, getLoggedInUser())) {
                        throw new NotAuthorizedException();
                    }
                    var recipeEntity = recipeService.getRecipeById(recipeId);
                    newWeekplanDay.getRecipes().add(WeekplanDayRecipe.builder()
                            .isSimpleRecipe(false)
                            .recipe(recipeEntity)
                            .build());
                }
                case SIMPLE_RECIPE -> {
                    var simpleRecipe = (WeekplanDayPut.SimpleRecipe) recipe;

                    newWeekplanDay.getRecipes().add(WeekplanDayRecipe.builder()
                            .isSimpleRecipe(true)
                            // JPA wants an null id if entries needs to be created
                            .id(simpleRecipe.getId().equals("") ? null : simpleRecipe.getId())
                            .simpleRecipeText(simpleRecipe.getTitle())
                            .build());
                }
            }
        }
    }
}

