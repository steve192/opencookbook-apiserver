package com.sterul.opencookbookapiserver.entities.shopping;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import com.sterul.opencookbookapiserver.entities.AuditableEntity;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/** How often in a row somebody left a line unticked when importing, and whether that made it a staple. */
@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
public class ShoppingStaple extends AuditableEntity {

    /** Unticked this many imports in a row, a line starts unticked. */
    public static final int STREAK_FOR_STAPLE = 3;

    @Id
    @SequenceGenerator(name = "shopping_staple_seq", sequenceName = "shopping_staple_seq", allocationSize = 1)
    @GeneratedValue(generator = "shopping_staple_seq")
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private CookpalUser user;

    @Column(nullable = false)
    private String nameKey;

    @Column(nullable = false)
    private String name;

    private int untickedStreak;

    private boolean staple;

    public void observe(boolean ticked) {
        untickedStreak = ticked ? 0 : untickedStreak + 1;
        staple = untickedStreak >= STREAK_FOR_STAPLE;
    }
}
