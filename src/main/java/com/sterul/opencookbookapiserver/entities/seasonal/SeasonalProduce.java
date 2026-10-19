package com.sterul.opencookbookapiserver.entities.seasonal;

import java.util.HashSet;
import java.util.Set;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import com.sterul.opencookbookapiserver.entities.catalogue.CatalogueFood;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** A fruit or vegetable of the seasonal calendar: when it grows per region, and how it keeps at home. */
@Entity
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeasonalProduce {

    @Id
    private Long id;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "catalogue_food_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private CatalogueFood food;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SeasonCategory category;

    @ElementCollection
    @CollectionTable(name = "seasonal_produce_month", joinColumns = @JoinColumn(name = "catalogue_food_id"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    @Builder.Default
    private Set<SeasonMonth> months = new HashSet<>();

    @Embedded
    @Builder.Default
    private StorageFacts storage = new StorageFacts();

    /** Grown outdoors in that month of that region; greenhouse and stored produce do not count. */
    public boolean isInSeason(String region, int month) {
        return months.stream().anyMatch(season -> season.getRegion().equals(region) && season.getMonth() == month
                && season.getMethod().isInSeason());
    }
}
