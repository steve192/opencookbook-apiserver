package com.sterul.opencookbookapiserver.entities;

import lombok.Data;

import javax.persistence.*;
import java.util.Date;

@Embeddable
@Data
public class ChangeInformationEmbeddable {

    @Temporal(TemporalType.TIMESTAMP)
    @Column(updatable = false)
    private Date createdOn;

    @Temporal(TemporalType.TIMESTAMP)
    private Date lastChange;

    @PrePersist
    private void popuplateDates() {
        createdOn = new Date();
        lastChange = createdOn;
    }

    @PreUpdate
    private void updateDates() {
        lastChange = new Date();
    }
}
