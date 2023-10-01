package com.sterul.opencookbookapiserver.repositoriespostgress.entities.recipe;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.ManyToOne;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.sterul.opencookbookapiserver.repositoriespostgress.entities.AuditableEntity;
import com.sterul.opencookbookapiserver.repositoriespostgress.entities.account.CookpalUser;

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
    @GeneratedValue
    private Long id;

    private String title;


    @ManyToOne
    @JsonIgnore
    private CookpalUser owner;

}
