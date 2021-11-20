package com.sterul.opencookbookapiserver.entities.recipe;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.ManyToOne;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.sterul.opencookbookapiserver.entities.account.User;

import lombok.Getter;
import lombok.Setter;

@Entity
public class RecipeGroup {
    @Id
    @GeneratedValue
    @Getter
    @Setter
    private Long id;

    @Getter
    @Setter
    private String title;

    @ManyToOne
    @JsonIgnore
    @Getter
    @Setter
    private User owner;

}
