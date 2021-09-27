package com.sterul.opencookbookapiserver.entities;

import java.util.List;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.OneToMany;


@Entity
public class Recipe {
    private @Id @GeneratedValue Long id;
    private String title;

    @OneToMany( cascade= CascadeType.ALL)
    private List<IngredientNeed> neededIngredients;

    @OneToMany( cascade = CascadeType.ALL )
    private List<RecipePreparationStep> preparationSteps;

    public List<RecipePreparationStep> getPreparationSteps() {
        return preparationSteps;
    }

    public void setPreparationSteps(List<RecipePreparationStep> preparationSteps) {
        this.preparationSteps = preparationSteps;
    }

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
