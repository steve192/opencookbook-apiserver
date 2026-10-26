package com.sterul.opencookbookapiserver.entities.shopping;

import java.util.HashMap;
import java.util.Map;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import com.sterul.opencookbookapiserver.entities.catalogue.CatalogueFood;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** A catalogue food a shopping list offers to tap. */
@Entity
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShoppingTile {

    @Id
    private Long id;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "catalogue_food_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private CatalogueFood food;

    /** Per language, the word a shop list uses ("Reis"), which is often not the food's display name. */
    @ElementCollection
    @CollectionTable(name = "shopping_tile_name", joinColumns = @JoinColumn(name = "catalogue_food_id"))
    @MapKeyColumn(name = "language_iso_code")
    @Column(name = "name", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @Builder.Default
    private Map<String, String> names = new HashMap<>();
}
