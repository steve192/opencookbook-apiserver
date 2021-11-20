package com.sterul.opencookbookapiserver.entities.account;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Getter;
import lombok.Setter;

@Entity
public class User {

    @Id
    @GeneratedValue
    @Getter
    @Setter
    private Long userId;
    @Getter
    @Setter
    private String emailAddress;

    @JsonIgnore
    @Getter
    @Setter
    private String passwordHash;


}
