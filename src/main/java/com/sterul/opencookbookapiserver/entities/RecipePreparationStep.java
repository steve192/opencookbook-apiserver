package com.sterul.opencookbookapiserver.entities;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;

import io.swagger.v3.oas.annotations.Hidden;

@Entity
public class RecipePreparationStep {
    
    private @Id @GeneratedValue  Long id;

    private String description;


    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

}
