package com.sterul.opencookbookapiserver.entities.recipe;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.sterul.opencookbookapiserver.entities.AuditableEntity;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RecipeGroup extends AuditableEntity {
    @Id
    @SequenceGenerator(name = "recipe_group_seq", sequenceName = "recipe_group_seq", allocationSize = 1)
    @GeneratedValue(generator = "recipe_group_seq")
    private Long id;

    private String title;

    @ManyToOne
    @JsonIgnore
    private CookpalUser owner;

}
