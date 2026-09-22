package com.sterul.opencookbookapiserver.entities.household;

import java.time.Instant;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.annotations.UuidGenerator;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.sterul.opencookbookapiserver.entities.AuditableEntity;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * A single-use link to join one household; accepting it deletes it. It grants membership rather
 * than access, which is why it is not a {@code Share}.
 */
@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
public class HouseholdInvite extends AuditableEntity {

    /** The token itself. Pinned random, like {@link Household#getId()}. */
    @Id
    @UuidGenerator(style = UuidGenerator.Style.RANDOM)
    @EqualsAndHashCode.Include
    private String id;

    @JsonIgnore
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Household household;

    /** Null once the member who created it has left; the invite outlives them. */
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private CookpalUser createdBy;

    @Column(nullable = false)
    private Instant expiresAt;

    public boolean hasExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }
}
