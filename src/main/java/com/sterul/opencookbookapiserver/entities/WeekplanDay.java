package com.sterul.opencookbookapiserver.entities;

import java.util.Date;
import java.util.List;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import javax.persistence.TemporalType;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.sterul.opencookbookapiserver.entities.account.User;
import com.sterul.opencookbookapiserver.entities.recipe.Recipe;

import lombok.Data;

@Entity
@Data
public class WeekplanDay {
    @Id
    @GeneratedValue
    private Long id;

    @javax.persistence.Temporal(TemporalType.DATE)
    private Date day;

    @ManyToOne
    @JsonIgnore
    private User owner;

    @ManyToMany
    private List<Recipe> recipes;

}
