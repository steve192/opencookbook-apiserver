package com.sterul.opencookbookapiserver.entities;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.entities.nutrition.CatalogueFood;
import com.sterul.opencookbookapiserver.entities.nutrition.IngredientRelinkRun;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/** A user's ingredient, one per name, optionally linked to a catalogue food. */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(name = "ingredient_owner_name_unique", columnNames = {"owner_user_id", "name"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Ingredient extends AuditableEntity {
    @Id
    @SequenceGenerator(name = "ingredient_seq", sequenceName = "ingredient_seq", allocationSize = 1)
    @GeneratedValue(generator = "ingredient_seq")
    private Long id;

    @Column(nullable = false)
    private String name;

    private String additionalInfo;

    @ManyToOne(optional = false)
    private CookpalUser owner;

    @ManyToOne
    private CatalogueFood catalogueFood;

    /** Null while never matched. */
    @Enumerated(EnumType.STRING)
    private LinkSource linkSource;

    /** Null for links a person made. */
    private Float linkConfidence;

    /** 0 for links migrated from before the catalogue. */
    private Integer linkMatcherVersion;

    private Instant linkedAt;

    private boolean excludedFromNutrition;

    /** The relink run that made the current link, if any. */
    @ManyToOne(fetch = FetchType.LAZY)
    @EqualsAndHashCode.Exclude
    private IngredientRelinkRun linkRun;

    /** The owner's piece weights in grams, by unit key. */
    @ElementCollection
    @CollectionTable(name = "ingredient_portion_override", joinColumns = @JoinColumn(name = "ingredient_id"))
    @MapKeyColumn(name = "unit_key", length = 32)
    @Column(name = "grams", nullable = false)
    @Builder.Default
    @EqualsAndHashCode.Exclude
    private Map<String, Float> portionOverrides = new HashMap<>();

    /**
     * @param food null if nothing matched well enough
     * @param run  null outside relink runs
     */
    public void linkAutomatically(CatalogueFood food, Float confidence, int matcherVersion, Instant at, IngredientRelinkRun run) {
        decide(food, LinkSource.AUTO, food == null ? null : confidence, matcherVersion, at, false);
        this.linkRun = run;
    }

    public void linkByOwner(CatalogueFood food, Instant at) {
        decide(food, LinkSource.USER, null, null, at, false);
    }

    public void excludeByOwner(Instant at) {
        decide(null, LinkSource.USER, null, null, at, true);
    }

    public void linkByAdmin(CatalogueFood food, Instant at) {
        decide(food, LinkSource.ADMIN, null, null, at, false);
    }

    public void excludeByAdmin(Instant at) {
        decide(null, LinkSource.ADMIN, null, null, at, true);
    }

    /** Automatic matching never overrides a person's decision. */
    public boolean isDecidedByPerson() {
        return linkSource == LinkSource.USER || linkSource == LinkSource.ADMIN;
    }

    /** Takes over the other's link when merging, unless this one already has a decision. */
    public void adoptLinkOf(Ingredient other) {
        if (catalogueFood != null || excludedFromNutrition || isDecidedByPerson()
                || (other.catalogueFood == null && !other.excludedFromNutrition)) {
            return;
        }
        decide(other.catalogueFood, other.linkSource, other.linkConfidence, other.linkMatcherVersion, other.linkedAt,
                other.excludedFromNutrition);
    }

    public void restoreLink(CatalogueFood food, LinkSource source, Float confidence, Integer matcherVersion, Instant at) {
        decide(food, source, confidence, matcherVersion, at, false);
    }

    private void decide(CatalogueFood food, LinkSource source, Float confidence, Integer matcherVersion, Instant at, boolean excluded) {
        this.catalogueFood = food;
        this.linkSource = source;
        this.linkConfidence = confidence;
        this.linkMatcherVersion = matcherVersion;
        this.linkedAt = at;
        this.excludedFromNutrition = excluded;
        this.linkRun = null;
    }

    public enum LinkSource {
        /** By the matcher; may be relinked. */
        AUTO,
        USER,
        ADMIN
    }

    @Override
    public String toString() {
        return id + " " + name;
    }
}
