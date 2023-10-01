package com.sterul.opencookbookapiserver.repositoriespostgress.entities.account;

import java.util.Calendar;
import java.util.Date;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.OneToOne;
import javax.persistence.PrePersist;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;

import org.hibernate.annotations.GenericGenerator;

import com.sterul.opencookbookapiserver.repositoriespostgress.entities.AuditableEntity;

import lombok.Data;

@Entity
@Data
public class PasswordResetLink extends AuditableEntity {
    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    private String id;

    @Temporal(TemporalType.TIMESTAMP)
    private Date validUntil;

    @OneToOne
    private CookpalUser user;

    @PrePersist
    private void prePersist() {
        var now = Calendar.getInstance();
        now.add(Calendar.HOUR, 1);
        validUntil = now.getTime();
    }
}
