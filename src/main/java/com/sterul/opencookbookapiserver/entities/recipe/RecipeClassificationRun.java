package com.sterul.opencookbookapiserver.entities.recipe;

import com.sterul.opencookbookapiserver.entities.ReviewedRun;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/** A reviewed pass classifying recipes. */
@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
public class RecipeClassificationRun extends ReviewedRun {

    @Id
    @SequenceGenerator(name = "recipe_classification_run_seq", sequenceName = "recipe_classification_run_seq", allocationSize = 1)
    @GeneratedValue(generator = "recipe_classification_run_seq")
    @EqualsAndHashCode.Include
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ClassificationKind kind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Scope scope;

    /** What the values were read from, such as a dataset release, so a later run explains a different outcome. */
    private String basis;

    /** Looked at and left alone because they cannot be read; the reason is on each proposal. */
    private int unreadableCount;

    /** A value a person set is never in scope, whatever the scope says. */
    public enum Scope {
        /** The normal backfill. */
        NEVER_CLASSIFIED,
        /** Re-read everything a previous run derived, for instance after a dataset release. */
        DERIVED_ONLY
    }
}
