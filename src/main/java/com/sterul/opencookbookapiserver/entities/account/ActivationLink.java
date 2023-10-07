package com.sterul.opencookbookapiserver.entities.account;

import org.hibernate.annotations.UuidGenerator;

import com.sterul.opencookbookapiserver.entities.AuditableEntity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToOne;
import lombok.Data;

@Entity
@Data
public class ActivationLink extends AuditableEntity {
    @Id
    @UuidGenerator
    private String id;

    @OneToOne
    private CookpalUser user;
}
