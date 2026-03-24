package com.sterul.opencookbookapiserver.entities.account;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.hibernate.annotations.UuidGenerator;

import com.sterul.opencookbookapiserver.entities.AuditableEntity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import lombok.Data;

@Entity
@Data
public class PasswordResetLink extends AuditableEntity {
    @Id
    @UuidGenerator
    private String id;

    private Instant validUntil;

    @OneToOne
    private CookpalUser user;

    @PrePersist
    private void prePersist() {
        validUntil = Instant.now().plus(1, ChronoUnit.HOURS);
    }
}
