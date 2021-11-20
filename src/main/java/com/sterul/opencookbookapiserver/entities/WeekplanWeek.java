package com.sterul.opencookbookapiserver.entities;

import java.util.List;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.sterul.opencookbookapiserver.entities.account.User;
import com.sterul.opencookbookapiserver.entities.recipe.Recipe;

import lombok.Data;

@Entity
@Data
public class WeekplanWeek {
    @Id
    @GeneratedValue
    private Long id;

    private int year;
    private int weekNumber;

    @ManyToOne
    @JsonIgnore
    private User owner;

    @OneToMany(cascade = CascadeType.ALL)
    private List<WeekplanDay> weekDays;

    /**
     * WeekplanDay
     */
    @Entity
    @Data
    private class WeekplanDay {
        @Id
        @GeneratedValue
        private Long id;

        @ManyToMany
        private List<Recipe> recipes;


    }
}
