package com.sterul.opencookbookapiserver.entities.recipe;

import java.util.List;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.sterul.opencookbookapiserver.entities.IngredientNeed;
import com.sterul.opencookbookapiserver.entities.RecipeImage;
import com.sterul.opencookbookapiserver.entities.account.User;

import lombok.Data;

@Entity
@Data
public class Recipe {
    @Id 
    @GeneratedValue 
    private Long id;

    private String title;

    @OneToMany(cascade = CascadeType.ALL)
    private List<IngredientNeed> neededIngredients;

    @ElementCollection
    @Column(length = 10000)
    private List<String> preparationSteps;

    @ManyToOne
    @JsonIgnore
    private User owner;

    @OneToMany
    private List<RecipeImage> images;

    private int servings;

    @ManyToMany
    private List<RecipeGroup> recipeGroups;


}
