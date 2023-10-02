package com.sterul.opencookbookapiserver.repositoriespostgress.entities;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.ManyToOne;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.sterul.opencookbookapiserver.repositoriespostgress.entities.account.CookpalUser;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecipeImage extends AuditableEntity {

    @Id
    private String uuid;

    @JsonIgnore
    @ManyToOne
    private CookpalUser owner;

}
