package com.sterul.opencookbookapiserver.entities.account;

import java.util.Calendar;
import java.util.Date;

import org.hibernate.annotations.GenericGenerator;

import com.sterul.opencookbookapiserver.entities.AuditableEntity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import lombok.Data;

@Entity
@Data
public class PasswordResetLink extends AuditableEntity {
    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(
            name = "UUID",
            strategy = "org.hibernate.id.UUIDGenerator"
    )
    private String id;

    @Temporal(TemporalType.TIMESTAMP)
    private Date validUntil;

    @OneToOne
    private User user;

    @PrePersist
    private void prePersist() {
        var now = Calendar.getInstance();
        now.add(Calendar.HOUR, 1);
        validUntil = now.getTime();
    }
}
