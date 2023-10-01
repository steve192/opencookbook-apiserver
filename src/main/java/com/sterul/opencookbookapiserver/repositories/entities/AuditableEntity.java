package com.sterul.opencookbookapiserver.repositories.entities;

import java.time.Instant;

import javax.persistence.Column;
import javax.persistence.EntityListeners;
import javax.persistence.MappedSuperclass;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import lombok.Data;

@EntityListeners(AuditingEntityListener.class)
@MappedSuperclass
@Data
public abstract class AuditableEntity {
    @Column(updatable = false, name= "CREATED_ON")
    @CreatedDate
    private Instant createdOn;

    @LastModifiedDate
    @Column(name = "LAST_CHANGE")
    private Instant lastChange;
}
