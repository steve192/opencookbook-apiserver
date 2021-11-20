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

import lombok.Getter;
import lombok.Setter;

@Entity
public class Recipe {
    @Id 
    @GeneratedValue 
    @Getter
    @Setter 
    private Long id;

    @Getter
    @Setter
    private String title;

    @OneToMany(cascade = CascadeType.ALL)
    @Getter
    @Setter
    private List<IngredientNeed> neededIngredients;

    @ElementCollection
    @Column(length = 10000)
    @Getter
    @Setter
    private List<String> preparationSteps;

    @ManyToOne
    @JsonIgnore
    @Getter
    @Setter
    private User owner;

    @OneToMany
    @Getter
    @Setter
    private List<RecipeImage> images;

    @Getter
    @Setter
    private int servings;

    @ManyToMany
    @Getter
    @Setter
    private List<RecipeGroup> recipeGroups;


}
