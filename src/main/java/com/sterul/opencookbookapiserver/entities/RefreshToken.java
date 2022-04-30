package com.sterul.opencookbookapiserver.entities;

import com.sterul.opencookbookapiserver.entities.account.User;
import lombok.Data;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.validation.constraints.NotNull;
import java.time.Instant;

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
