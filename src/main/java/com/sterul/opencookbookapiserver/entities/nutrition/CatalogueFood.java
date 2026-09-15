package com.sterul.opencookbookapiserver.entities.nutrition;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.sterul.opencookbookapiserver.entities.AuditableEntity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Reference food ingredients link to. DATASET foods are read-only; CUSTOM foods belong to administrators.
 * A variant is a prepared form of its base ("boiled potato"); names belong to bases.
 */
@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
public class CatalogueFood extends AuditableEntity {

    @Id
    @SequenceGenerator(name = "catalogue_food_seq", sequenceName = "catalogue_food_seq", allocationSize = 1)
    @GeneratedValue(generator = "catalogue_food_seq")
    @EqualsAndHashCode.Include
    @ToString.Include
    private Long id;

    /** Stable across releases: "bls-K110100", "fdc-321358", "custom-...", "legacy-...". */
    @Column(nullable = false, unique = true)
    @ToString.Include
    private String catalogueKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Origin origin;

    @ManyToOne(fetch = FetchType.LAZY)
    private CatalogueFood variantOf;

    /** No longer shipped; kept so existing links survive. */
    private boolean retired;

    @Enumerated(EnumType.STRING)
    private SourceType sourceType;

    private String sourceCode;

    private String sourceName;

    /** Contributes next to nothing (water, salt, spices): a missing amount does not matter. */
    private boolean negligible;

    @Column(name = "density_g_per_ml")
    private Float densityGPerMl;

    @Embedded
    @Builder.Default
    private NutrientValues nutrients = new NutrientValues();

    @ElementCollection
    @CollectionTable(name = "catalogue_food_name", joinColumns = @JoinColumn(name = "catalogue_food_id"))
    @Builder.Default
    private List<CatalogueFoodName> names = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "catalogue_food_state", joinColumns = @JoinColumn(name = "catalogue_food_id"))
    @Column(name = "state_key")
    @Builder.Default
    private Set<String> states = new HashSet<>();

    @ElementCollection
    @CollectionTable(name = "catalogue_food_portion", joinColumns = @JoinColumn(name = "catalogue_food_id"))
    @Builder.Default
    private List<CatalogueFoodPortion> portions = new ArrayList<>();

    /** Falls back to any display name. */
    public Optional<String> displayName(String languageIsoCode) {
        var displayNames = names.stream().filter(CatalogueFoodName::isDisplay).toList();
        return displayNames.stream()
                .filter(name -> name.getLanguageIsoCode().equals(languageIsoCode))
                .findFirst()
                .or(() -> displayNames.stream().findFirst())
                .map(CatalogueFoodName::getName);
    }

    public boolean isReadOnly() {
        return origin == Origin.DATASET;
    }

    public enum Origin {
        DATASET, CUSTOM
    }

    public enum SourceType {
        BLS, FDC
    }
}
