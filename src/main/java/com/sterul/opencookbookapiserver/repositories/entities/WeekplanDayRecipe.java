package com.sterul.opencookbookapiserver.repositories.entities;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.OneToOne;

import org.hibernate.annotations.GenericGenerator;
import org.springframework.lang.Nullable;

import com.sterul.opencookbookapiserver.repositories.entities.recipe.Recipe;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeekplanDayRecipe {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(
            name = "UUID",
            strategy = "org.hibernate.id.UUIDGenerator"
    )
    private String id;

    @Nullable
    @OneToOne
    private Recipe recipe;

    private boolean isSimpleRecipe;

    private String simpleRecipeText;

    
}
