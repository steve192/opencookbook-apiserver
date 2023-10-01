package com.sterul.opencookbookapiserver.entities;

import com.sterul.opencookbookapiserver.entities.account.CookpalUser;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
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
