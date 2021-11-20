package com.sterul.opencookbookapiserver.entities.recipe;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.ManyToOne;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.sterul.opencookbookapiserver.entities.account.User;

import lombok.Data;

@Entity
@Data
public class RecipeGroup {
    @Id
    @GeneratedValue
    private Long id;

    private String title;

    @ManyToOne
    @JsonIgnore
    private User owner;

}
