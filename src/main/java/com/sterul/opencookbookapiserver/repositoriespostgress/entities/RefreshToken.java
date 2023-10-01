package com.sterul.opencookbookapiserver.repositoriespostgress.entities;

import java.time.Instant;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.validation.constraints.NotNull;

import com.sterul.opencookbookapiserver.repositoriespostgress.entities.account.CookpalUser;

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
    private CookpalUser owner;
}
