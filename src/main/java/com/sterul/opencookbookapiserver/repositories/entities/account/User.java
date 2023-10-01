package com.sterul.opencookbookapiserver.repositories.entities.account;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Table;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.sterul.opencookbookapiserver.repositories.entities.AuditableEntity;

import lombok.Data;

@Entity
@Data
@Table(name = "\"USER\"")
public class User extends AuditableEntity {

    @Id
    @GeneratedValue
    @Column(name = "USER_ID")
    private Long userId;
    @Column(name = "EMAIL_ADDRESS")
    private String emailAddress;

    @JsonIgnore
    @Column(name = "PASSWORD_HASH")
    private String passwordHash;

    @Column(name = "ACTIVATED")
    private boolean activated;

    @Override
    public String toString() {
        return getUserId() + " " + getEmailAddress();
    }
}
