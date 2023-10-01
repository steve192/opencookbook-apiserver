package com.sterul.opencookbookapiserver.repositories.entities.account;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Table;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.sterul.opencookbookapiserver.repositories.entities.AuditableEntity;

import lombok.Data;

@Entity
@Data
@Table(name = "USER")
public class User extends AuditableEntity {

    @Id
    @GeneratedValue
    private Long userId;
    private String emailAddress;

    @JsonIgnore
    private String passwordHash;

    private boolean activated;

    @Override
    public String toString() {
        return getUserId() + " " + getEmailAddress();
    }
}
