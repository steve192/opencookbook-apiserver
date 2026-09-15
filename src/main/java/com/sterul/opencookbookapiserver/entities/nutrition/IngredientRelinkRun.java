package com.sterul.opencookbookapiserver.entities.nutrition;

import java.time.Instant;

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

/** A reviewed relinking pass: previewed, decided, applied and possibly reverted. */
@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
public class IngredientRelinkRun extends AuditableEntity {

    @Id
    @SequenceGenerator(name = "ingredient_relink_run_seq", sequenceName = "ingredient_relink_run_seq", allocationSize = 1)
    @GeneratedValue(generator = "ingredient_relink_run_seq")
    @EqualsAndHashCode.Include
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Scope scope;

    /** Only for AUTOMATIC_BELOW_CONFIDENCE. */
    private Float belowConfidence;

    private int matcherVersion;

    private String datasetLabel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    private int proposalCount;
    private int ingredientCount;
    private int unchangedCount;
    private int appliedCount;
    /** Accepted but changed since the preview. */
    private int skippedCount;

    @ManyToOne
    private CookpalUser startedBy;

    private Instant appliedAt;
    private Instant revertedAt;

    /** Links decided by a person are never in scope. */
    public enum Scope {
        NEVER_MATCHED_OR_UNLINKED,
        AUTOMATIC_BELOW_CONFIDENCE,
        ALL_AUTOMATIC,
        RETIRED_FOODS,
        OLDER_MATCHER
    }

    public enum Status {
        PREVIEWED, APPLIED, REVERTED, DISCARDED
    }
}
