package com.sterul.opencookbookapiserver.controllers.responses;

import java.util.Date;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.Data;

@Data
public class WeekplanDayResponse {

    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date day;
    private List<MinimalRecipe> recipes;

    @Data
    public class MinimalRecipe {
        private Long id;
        private String title;
        private String titleImageUuid;
    }
}
