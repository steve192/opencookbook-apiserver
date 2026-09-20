package com.sterul.opencookbookapiserver.entities.planning;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import com.sterul.opencookbookapiserver.entities.recipe.MealType;
import com.sterul.opencookbookapiserver.entities.recipe.Recipe;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import lombok.ToString;

/** One meal of a proposed week, with the terms that placed its recipe there. */
@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
public class PlanDraftSlot {

    /** The order of a week: by day, then by meal. */
    public static final Comparator<PlanDraftSlot> DAY_ORDER =
            Comparator.comparing(PlanDraftSlot::getPlanDate).thenComparing(PlanDraftSlot::getMealType);

    @Id
    @SequenceGenerator(name = "plan_draft_slot_seq", sequenceName = "plan_draft_slot_seq", allocationSize = 1)
    @GeneratedValue(generator = "plan_draft_slot_seq")
    @EqualsAndHashCode.Include
    @ToString.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private PlanDraft draft;

    @Column(nullable = false)
    @ToString.Include
    private LocalDate planDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @ToString.Include
    private MealType mealType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SlotKind kind;

    /** Null for a gap, and for a cooked slot the pool had nothing for. */
    @ManyToOne
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private Recipe recipe;

    /** How many servings are cooked; more than the household where some are eaten later as leftovers. */
    private Integer servings;

    /** For a LEFTOVER: the slot where it was cooked. */
    @ManyToOne(fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private PlanDraftSlot leftoverOf;

    /** Kept as it is when the rest of the draft is drawn again. */
    private boolean locked;

    @ElementCollection
    @CollectionTable(name = "plan_draft_slot_term", joinColumns = @JoinColumn(name = "slot_id"))
    @Builder.Default
    @OnDelete(action = OnDeleteAction.CASCADE)
    private List<PlanSlotTerm> terms = new ArrayList<>();
}
