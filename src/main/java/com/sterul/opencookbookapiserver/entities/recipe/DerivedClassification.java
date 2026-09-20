package com.sterul.opencookbookapiserver.entities.recipe;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Marks a recipe's value of one kind as derived by a run rather than chosen by a person. Only a
 * marked value may be redone or reverted; a value without a mark is a person's and stays theirs.
 */
@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class DerivedClassification {

    @Id
    @SequenceGenerator(name = "derived_classification_seq", sequenceName = "derived_classification_seq", allocationSize = 1)
    @GeneratedValue(generator = "derived_classification_seq")
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Recipe recipe;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ClassificationKind kind;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private RecipeClassificationRun run;
}
