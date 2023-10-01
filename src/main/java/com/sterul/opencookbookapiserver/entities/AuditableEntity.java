package com.sterul.opencookbookapiserver.entities;

import java.time.Instant;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Data;

@EntityListeners(AuditingEntityListener.class)
@MappedSuperclass
@Data
public abstract class AuditableEntity {
    @Column(updatable = false)
    @CreatedDate
    private Instant createdOn;

    @LastModifiedDate
    private Instant lastChange;
}
