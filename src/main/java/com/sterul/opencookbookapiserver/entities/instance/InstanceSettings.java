package com.sterul.opencookbookapiserver.entities.instance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import lombok.Data;

/** What an administrator chose for the whole instance. A single row, inserted by the migration. */
@Entity
@Data
public class InstanceSettings {

    public static final long ROW_ID = 1;

    @Id
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SignupMode signupMode;
}
