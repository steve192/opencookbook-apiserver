package com.sterul.opencookbookapiserver.entities;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import javax.persistence.Column;
import javax.persistence.EntityListeners;
import javax.persistence.MappedSuperclass;
import java.time.Instant;

@EntityListeners(AuditingEntityListener.class)
@MappedSuperclass
public abstract class AuditableEntity {
    @Column(updatable = false)
    @CreatedDate
    private Instant createdOn;

    @LastModifiedDate
    private Instant lastChange;
}
