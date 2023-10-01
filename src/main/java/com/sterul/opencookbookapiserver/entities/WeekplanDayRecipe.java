package com.sterul.opencookbookapiserver.entities;

import org.hibernate.annotations.GenericGenerator;
import org.springframework.lang.Nullable;

import com.sterul.opencookbookapiserver.entities.recipe.Recipe;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.OneToOne;
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
