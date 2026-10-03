package com.sterul.opencookbookapiserver.entities.account;

import java.time.Instant;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import com.sterul.opencookbookapiserver.entities.AuditableEntity;

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

/** A single-use link to create an active account; signing up with it deletes it. */
@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
public class Invitation extends AuditableEntity {

    /** The token itself. */
    @Id
    @Column(length = 43)
    @EqualsAndHashCode.Include
    private String id;

    /** Null once the administrator who created it is deleted; the invitation outlives them. */
    @ManyToOne(fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private CookpalUser createdBy;

    @Column(nullable = false)
    private Instant expiresAt;

    public boolean hasExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }
}
