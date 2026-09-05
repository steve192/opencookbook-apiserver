package com.sterul.opencookbookapiserver.entities.ml;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.sterul.opencookbookapiserver.entities.AuditableEntity;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/** One piece of work handed to the machine learning subsystem. */
@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class MlJob extends AuditableEntity {

    @Id
    private String id;

    @ManyToOne
    @JsonIgnore
    private CookpalUser owner;

    // Lengths spelled out so that the entity and V13__.sql describe the same column: a schema
    // built from the entities would otherwise be wider than the one a migrated database has.
    @Column(length = 64)
    private String jobType;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private MlJobStatus status;

    /** The subsystem's own id for this job. */
    private String remoteJobId;

    /** The subsystem's result, kept as it arrived. */
    @Column(length = 100000)
    private String result;

    private String errorCode;

    @Column(length = 1000)
    private String errorMessage;

    private boolean errorRetryable;

    /** How many jobs the subsystem would run before this one, as of the last time it was asked. */
    private Integer queuePosition;

    private Instant finishedAt;

    /** Whether this scan still counts against its owner's daily allowance. */
    @Builder.Default
    private boolean countsTowardsQuota = true;

    public boolean isFinished() {
        return status != null && status.isFinished();
    }
}
