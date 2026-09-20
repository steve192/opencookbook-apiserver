package com.sterul.opencookbookapiserver.entities;

import java.time.Instant;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import com.sterul.opencookbookapiserver.entities.account.CookpalUser;

import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

/**
 * A pass that changes many rows under review: previewed, decided, applied and possibly reverted.
 * What is proposed differs per run type; the lifecycle does not.
 */
@MappedSuperclass
@Getter
@Setter
public abstract class ReviewedRun extends AuditableEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status = Status.PREVIEWED;

    private int proposalCount;
    private int appliedCount;
    /** Accepted, but changed since the preview and so left alone by apply. */
    private int skippedCount;

    @ManyToOne
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private CookpalUser startedBy;

    private Instant appliedAt;
    private Instant revertedAt;

    public enum Status {
        PREVIEWED, APPLIED, REVERTED, DISCARDED
    }

    public abstract Long getId();

    public void markApplied(Instant at, int applied, int skipped) {
        status = Status.APPLIED;
        appliedAt = at;
        appliedCount = applied;
        skippedCount = skipped;
    }

    public void markReverted(Instant at) {
        status = Status.REVERTED;
        revertedAt = at;
    }

    public void discard() {
        status = Status.DISCARDED;
    }
}
