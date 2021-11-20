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

@Entity
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
    private class WeekplanDay {
        @Id
        @GeneratedValue
        private Long id;

        @ManyToMany
        private List<Recipe> recipes;

        public List<Recipe> getRecipes() {
            return recipes;
        }

        public void setRecipes(List<Recipe> recipes) {
            this.recipes = recipes;
        }

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public int getYear() {
            return year;
        }

        public void setYear(int year) {
            this.year = year;
        }

        public int getWeekNumber() {
            return weekNumber;
        }

        public void setWeekNumber(int weekNumber) {
            this.weekNumber = weekNumber;
        }

        public User getOwner() {
            return owner;
        }

        public void setOwner(User owner) {
            this.owner = owner;
        }

        public List<WeekplanDay> getWeekDays() {
            return weekDays;
        }

        public void setWeekDays(List<WeekplanDay> weekDays) {
            this.weekDays = weekDays;
        }

    }
}
