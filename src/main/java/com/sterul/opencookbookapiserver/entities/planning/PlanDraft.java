package com.sterul.opencookbookapiserver.entities.planning;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import com.sterul.opencookbookapiserver.entities.AuditableEntity;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.SequenceGenerator;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * A proposed week. Nothing reaches the weekplan until the cook accepts it, so a generate over a week
 * that is already partly planned can never destroy anything by surprise.
 */
@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
public class PlanDraft extends AuditableEntity {

    @Id
    @SequenceGenerator(name = "plan_draft_seq", sequenceName = "plan_draft_seq", allocationSize = 1)
    @GeneratedValue(generator = "plan_draft_seq")
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private CookpalUser owner;

    /** The answers it was generated from; null once that profile is deleted, by which time the draft is closed. */
    @ManyToOne
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private PlanningProfile profile;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private int days;

    /** Makes the draft reproducible, and a reroll a deliberate new draw. */
    private long seed;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @Builder.Default
    private Status status = Status.DRAFT;

    @OneToMany(mappedBy = "draft", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("planDate, mealType, id")
    @Builder.Default
    private List<PlanDraftSlot> slots = new ArrayList<>();

    public enum Status {
        DRAFT, ACCEPTED, DISCARDED
    }

    public boolean isOpen() {
        return status == Status.DRAFT;
    }
}
