package com.sterul.opencookbookapiserver.entities;

import java.util.List;
import javax.persistence.CascadeType;
import javax.persistence.ElementCollection;
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

    @ElementCollection
    private List<String> preparationSteps;

    @ElementCollection
    private List<RecipeImage> images;

    public List<RecipeImage> getImages() {
        return images;
    }



    public void setImages(List<RecipeImage> images) {
        this.images = images;
    }



    public List<String> getPreparationSteps() {
        return preparationSteps;
    }



    public void setPreparationSteps(List<String> preparationSteps) {
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
