package com.sterul.opencookbookapiserver.entities;

import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.SequenceGenerator;
import lombok.Data;

@Entity
@Data
public class WeekplanDay extends AuditableEntity {
    @Id
    @SequenceGenerator(name = "weekplan_day_seq", sequenceName = "weekplan_day_seq", allocationSize = 1)
    @GeneratedValue(generator = "weekplan_day_seq")
    private Long id;

    @Column(name = "plan_date")
    private LocalDate planDate;

    @ManyToOne
    @JsonIgnore
    private CookpalUser owner;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    private List<WeekplanDayRecipe> recipes;
}
