package com.sterul.opencookbookapiserver.entities.account;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.sterul.opencookbookapiserver.entities.ChangeInformationEmbeddable;
import lombok.Data;

import javax.persistence.Embedded;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;

@Entity
@Data
public class User {

    @Id
    @GeneratedValue
    private Long userId;
    private String emailAddress;


    @Embedded
    private ChangeInformationEmbeddable changeInformationEmbeddable;

    @JsonIgnore
    private String passwordHash;

    private boolean activated;
}
