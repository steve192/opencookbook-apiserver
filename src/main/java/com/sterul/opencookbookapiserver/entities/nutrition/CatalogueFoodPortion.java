package com.sterul.opencookbookapiserver.entities.nutrition;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Weight of one count unit of a food ("piece", "clove"). */
@Embeddable
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CatalogueFoodPortion {

    @Column(name = "unit_key", nullable = false)
    private String unitKey;

    @Column(nullable = false)
    private float grams;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Origin origin;

    public enum Origin {
        SOURCE,
        /** Estimated during curation. */
        ESTIMATED,
        ADMIN
    }
}
