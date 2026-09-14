package com.sterul.opencookbookapiserver.entities.nutrition;

import com.sterul.opencookbookapiserver.entities.AuditableEntity;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/** An administrator's decision about an ingredient name that automatic linking follows. */
@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
public class CatalogueNameRule extends AuditableEntity {

    @Id
    @SequenceGenerator(name = "catalogue_name_rule_seq", sequenceName = "catalogue_name_rule_seq", allocationSize = 1)
    @GeneratedValue(generator = "catalogue_name_rule_seq")
    @EqualsAndHashCode.Include
    private Long id;

    /** Normalised, see IngredientNames. */
    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Kind kind;

    /** Only for NEVER_LINK_TO. */
    @ManyToOne
    private CatalogueFood catalogueFood;

    @ManyToOne
    private CookpalUser createdBy;

    public enum Kind {
        NEVER_LINK_TO,
        /** The name names no food ("Zahnstocher"). */
        NOT_A_FOOD
    }
}
