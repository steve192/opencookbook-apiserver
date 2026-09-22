package com.sterul.opencookbookapiserver.entities.household;

import org.hibernate.annotations.UuidGenerator;

import com.sterul.opencookbookapiserver.entities.AuditableEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/** A small private group with one shared cookbook and one shared weekplan. It owns no recipe. */
@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
public class Household extends AuditableEntity {

    /** Pinned random: it travels in urls, and a sequence or time-ordered id would be guessable. */
    @Id
    @UuidGenerator(style = UuidGenerator.Style.RANDOM)
    @EqualsAndHashCode.Include
    private String id;

    @Column(nullable = false, length = 64)
    private String name;
}
