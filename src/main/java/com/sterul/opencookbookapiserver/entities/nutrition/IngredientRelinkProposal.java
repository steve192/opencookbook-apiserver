package com.sterul.opencookbookapiserver.entities.nutrition;

import java.util.ArrayList;
import java.util.List;

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

/** A relink run's proposal for the ingredients sharing a name, language, food and confidence band. */
@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
public class IngredientRelinkProposal {

    @Id
    @SequenceGenerator(name = "ingredient_relink_proposal_seq", sequenceName = "ingredient_relink_proposal_seq", allocationSize = 1)
    @GeneratedValue(generator = "ingredient_relink_proposal_seq")
    @EqualsAndHashCode.Include
    @ToString.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private IngredientRelinkRun run;

    @Column(nullable = false)
    @ToString.Include
    private String name;

    private String language;

    @ManyToOne
    private CatalogueFood oldFood;

    @ManyToOne
    private CatalogueFood newFood;

    private Float newConfidence;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Change change;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Decision decision;

    @ElementCollection
    @CollectionTable(name = "ingredient_relink_member", joinColumns = @JoinColumn(name = "proposal_id"))
    @Builder.Default
    private List<IngredientRelinkMember> members = new ArrayList<>();

    public enum Change {
        NEW_LINK,
        CHANGED,
        UNLINKED,
        /** Same food, confidence in another band. */
        CONFIDENCE
    }

    public enum Decision {
        PENDING, ACCEPTED, REJECTED
    }
}
