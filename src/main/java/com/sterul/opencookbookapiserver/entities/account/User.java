package com.sterul.opencookbookapiserver.entities.account;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Data;

@Entity
@Data
public class User {

    @Id
    @GeneratedValue
    private Long userId;
    private String emailAddress;

    @JsonIgnore
    private String passwordHash;


}
