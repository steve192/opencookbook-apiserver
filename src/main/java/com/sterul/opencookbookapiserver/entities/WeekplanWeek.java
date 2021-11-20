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

import lombok.Getter;
import lombok.Setter;

@Entity
public class WeekplanWeek {
    @Id
    @GeneratedValue
    @Getter
    @Setter
    private Long id;

    @Getter
    @Setter
    private int year;
    @Getter
    @Setter
    private int weekNumber;

    @ManyToOne
    @JsonIgnore
    @Getter
    @Setter
    private User owner;

    @OneToMany(cascade = CascadeType.ALL)
    @Getter
    @Setter
    private List<WeekplanDay> weekDays;

    /**
     * WeekplanDay
     */
    @Entity
    private class WeekplanDay {
        @Id
        @GeneratedValue
        @Getter
        @Setter
        private Long id;

        @ManyToMany
        @Getter
        @Setter
        private List<Recipe> recipes;


    }
}
