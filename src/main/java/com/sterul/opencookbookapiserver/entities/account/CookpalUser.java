package com.sterul.opencookbookapiserver.entities.account;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.sterul.opencookbookapiserver.entities.AuditableEntity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CookpalUser extends AuditableEntity {

    @Id
    @SequenceGenerator(name = "cookpal_user_seq", sequenceName = "cookpal_user_seq", allocationSize = 1)
    @GeneratedValue(generator = "cookpal_user_seq")
    private Long userId;
    private String emailAddress;

    @JsonIgnore
    private String passwordHash;

    private boolean activated;

    @Enumerated(EnumType.STRING)
    private Role roles;

    @Override
    public String toString() {
        return getUserId() + " " + getEmailAddress();
    }
}
