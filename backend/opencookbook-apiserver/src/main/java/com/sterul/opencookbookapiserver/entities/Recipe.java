package com.sterul.opencookbookapiserver.entities;

import java.util.List;

import javax.persistence.CascadeType;
import javax.persistence.ElementCollection;
import javax.persistence.Embedded;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;

@Entity
public class Recipe {
    private @Id @GeneratedValue Long id;
    private String title;

    @OneToMany
    private List<IngredientNeed> neededIngredients;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public List<IngredientNeed> getNeededIngredients() {
        return neededIngredients;
    }

    public void setNeededIngredients(List<IngredientNeed> neededIngredients) {
        this.neededIngredients = neededIngredients;
    }

    
}
