package com.sterul.opencookbookapiserver.repositoriespostgress.entities;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.ManyToOne;

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
public class Ingredient extends AuditableEntity {
    @Id
    @GeneratedValue
    private Long id;

    private String name;

    @ManyToOne
    private CookpalUser owner;

    private boolean isPublicIngredient;

    @Override
    public String toString() {
        return id + " " + name;
    }
}
