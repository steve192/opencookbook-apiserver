package com.sterul.opencookbookapiserver.repositoriespostgress.entities.account;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.OneToOne;

import org.hibernate.annotations.GenericGenerator;

import com.sterul.opencookbookapiserver.repositoriespostgress.entities.AuditableEntity;

import lombok.Data;

@Entity
@Data
public class ActivationLink extends AuditableEntity {
    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(
            name = "UUID",
            strategy = "org.hibernate.id.UUIDGenerator"
    )
    private String id;

    @OneToOne
    private CookpalUser user;
}
