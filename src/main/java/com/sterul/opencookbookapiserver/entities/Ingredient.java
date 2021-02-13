package com.sterul.opencookbookapiserver.entities;


import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;

@Entity
public class Ingredient {
    private @Id @GeneratedValue Long id;
    private String name;

    // @ManyToMany(fetch = FetchType.LAZY)
    // private List<IngredientNeed> ingredientNeed;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
}
