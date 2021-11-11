package com.sterul.opencookbookapiserver.entities;

import java.util.List;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;

import com.sterul.opencookbookapiserver.entities.account.User;

@Entity
public class Recipe {
    private @Id @GeneratedValue Long id;
    private String title;

    @OneToMany(cascade = CascadeType.ALL)
    private List<IngredientNeed> neededIngredients;

    @ElementCollection
    @Column(length = 10000)
    private List<String> preparationSteps;

    @ManyToOne
    private User owner;

    @OneToMany
    private List<RecipeImage> images;

    private int servings;

    public User getOwner() {
        return owner;
    }

    public void setOwner(User owner) {
        this.owner = owner;
    }

    public List<RecipeImage> getImages() {
        return images;
    }

    public int getServings() {
        return servings;
    }

    public void setServings(int servings) {
        this.servings = servings;
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
