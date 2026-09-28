package com.sterul.opencookbookapiserver.entities.shopping;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A meal an item was imported for. */
@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ItemSource {

    @Column(nullable = false)
    private String title;

    /** Null for a recipe imported from the recipe screen. */
    private LocalDate planDate;
}
