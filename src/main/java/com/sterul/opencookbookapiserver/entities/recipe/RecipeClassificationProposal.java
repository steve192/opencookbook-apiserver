package com.sterul.opencookbookapiserver.entities.recipe;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/** What a run would make of one recipe, and what it was before, so applying can be undone exactly. */
@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class RecipeClassificationProposal {

    @Id
    @SequenceGenerator(name = "recipe_classification_proposal_seq", sequenceName = "recipe_classification_proposal_seq",
            allocationSize = 1)
    @GeneratedValue(generator = "recipe_classification_proposal_seq")
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private RecipeClassificationRun run;

    @ManyToOne(optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Recipe recipe;

    /** Encoded by the run's kind; null where the recipe could not be read. */
    private String proposedValue;

    /** Null when the recipe had none; kept so a revert restores exactly what was there. */
    private String previousValue;

    /** The run that derived the previous value; null where a person chose it or there was none. */
    @ManyToOne(fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private RecipeClassificationRun previousRun;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Decision decision;

    /**
     * Why the run reached this, in the reviewer's terms: the ingredient that decided a class, or
     * the one that stopped it being readable. A bare "this is MEAT" cannot be reviewed.
     */
    private String reason;

    public ClassificationSource previousSource() {
        if (previousRun != null) {
            return ClassificationSource.DERIVED;
        }
        return previousValue == null ? null : ClassificationSource.USER;
    }

    public boolean isDecidable() {
        return decision != Decision.SKIPPED;
    }

    public enum Decision {
        PENDING, ACCEPTED, REJECTED,
        /** Looked at and deliberately left alone: a person decided it, or it cannot be read. */
        SKIPPED
    }
}
