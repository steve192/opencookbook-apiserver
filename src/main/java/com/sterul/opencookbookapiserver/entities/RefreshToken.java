package com.sterul.opencookbookapiserver.entities;

import java.time.Instant;

import com.sterul.opencookbookapiserver.entities.account.User;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Entity
@Data
public class RefreshToken extends AuditableEntity {

    @Id
    @NotNull
    private String token;

    @NotNull
    private Instant validUntil;

    @ManyToOne
    private User owner;
}
