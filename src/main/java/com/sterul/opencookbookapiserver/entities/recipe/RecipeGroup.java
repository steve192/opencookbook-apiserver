package com.sterul.opencookbookapiserver.entities.recipe;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.ManyToOne;

import com.sterul.opencookbookapiserver.entities.account.User;

@Entity
public class RecipeGroup {
    @Id
    @GeneratedValue
    private Long id;

    private String title;

    @ManyToOne
    private User owner;

    public Long getId() {
        return id;
    }

    public User getOwner() {
        return owner;
    }

    public void setOwner(User owner) {
        this.owner = owner;
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
}
