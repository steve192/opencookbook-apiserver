package com.sterul.opencookbookapiserver.entities;

import java.time.LocalDate;
import java.util.List;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.entities.household.Household;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.SequenceGenerator;
import lombok.Data;

@Entity
@Data
public class WeekplanDay extends AuditableEntity implements ScopedEntity {
    @Id
    @SequenceGenerator(name = "weekplan_day_seq", sequenceName = "weekplan_day_seq", allocationSize = 1)
    @GeneratedValue(generator = "weekplan_day_seq")
    private Long id;

    @Column(name = "plan_date")
    private LocalDate planDate;

    /** Exactly one of these is set: a day belongs to a person or to a household, never both. */
    @ManyToOne
    @JsonIgnore
    private CookpalUser owner;

    @ManyToOne
    @JsonIgnore
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Household household;

    // Ordered: the meals of a day are shown, printed and reordered in list order, so the
    // order has to survive a round trip. Without an order column this is a bag and jpa
    // gives no such guarantee - a reorder was written and then read back arbitrarily.
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderColumn(name = "recipe_order")
    private List<WeekplanDayRecipe> recipes;
}
