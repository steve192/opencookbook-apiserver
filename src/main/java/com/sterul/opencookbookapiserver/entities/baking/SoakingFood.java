package com.sterul.opencookbookapiserver.entities.baking;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import com.sterul.opencookbookapiserver.entities.catalogue.CatalogueFood;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** A seed, flake or grain the bread tool puts into a soaker, with the water it soaks up. */
@Entity
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SoakingFood {

    @Id
    private Long id;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "catalogue_food_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private CatalogueFood food;

    /** Grams of water one gram soaks up. */
    @Column(nullable = false)
    private float absorption;
}
