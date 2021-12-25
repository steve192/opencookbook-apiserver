package com.sterul.opencookbookapiserver.entities;

import java.time.Instant;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.validation.constraints.NotNull;

import com.sterul.opencookbookapiserver.entities.account.User;

import lombok.Data;

@Entity
@Data
public class RefreshToken {

    @Id
    @NotNull
    private String token;

    @NotNull
    private Instant validUntil;

    @ManyToOne
    private User owner;
}
