package com.sterul.opencookbookapiserver.repositories.entities;

import java.util.Date;
import java.util.List;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.TemporalType;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.sterul.opencookbookapiserver.repositories.entities.account.User;

import lombok.Data;

@Entity
@Data
public class WeekplanDay extends AuditableEntity {
    @Id
    @GeneratedValue
    private Long id;

    @javax.persistence.Temporal(TemporalType.DATE)
    private Date day;

    @ManyToOne
    @JsonIgnore
    private User owner;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    private List<WeekplanDayRecipe> recipes;
}
