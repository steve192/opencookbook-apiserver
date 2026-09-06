package com.sterul.opencookbookapiserver.entities;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.UuidGenerator;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;

import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BringExport extends AuditableEntity {

    /** How long an export stays fetchable. */
    public static final Duration LIFETIME = Duration.ofMinutes(5);

    @Id
    @UuidGenerator
    private String id;

    @JsonIgnore
    @ManyToOne
    private CookpalUser owner;

    private int baseAmount;

    @ElementCollection
    @Column(length = 10000)
    private List<String> ingredients = new ArrayList<>();

    /** Null until it has been stored. */
    public Instant getExpiresAt() {
        return getCreatedOn() == null ? null : getCreatedOn().plus(LIFETIME);
    }

    public boolean hasExpired(Instant now) {
        var expiresAt = getExpiresAt();
        return expiresAt != null && !expiresAt.isAfter(now);
    }
}
