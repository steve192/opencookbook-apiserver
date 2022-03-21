package com.sterul.opencookbookapiserver.entities.account;

import lombok.Data;
import org.hibernate.annotations.GenericGenerator;

import javax.persistence.*;
import java.util.Calendar;
import java.util.Date;

@Entity
@Data
public class PasswordResetLink {
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
