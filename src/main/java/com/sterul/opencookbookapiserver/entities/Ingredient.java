package com.sterul.opencookbookapiserver.entities;

import com.sterul.opencookbookapiserver.entities.account.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.ManyToOne;

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
    private User owner;

    private boolean isPublicIngredient;
}
