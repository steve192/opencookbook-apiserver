package com.sterul.opencookbookapiserver.controllers.requests;

import java.util.List;

import lombok.Data;

@Data
public class WeekplanDayPut {
    private List<Long> recipeIds;

}
