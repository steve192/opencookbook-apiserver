package com.sterul.opencookbookapiserver.entities;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.ManyToOne;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.sterul.opencookbookapiserver.entities.account.User;

import org.hibernate.annotations.GenericGenerator;

import lombok.Data;

@Entity
@Data
public class RecipeImage {
    
    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(
        name = "UUID",
        strategy = "org.hibernate.id.UUIDGenerator"
    )
    private String uuid;

    @JsonIgnore
    @ManyToOne
    private User owner;


}
