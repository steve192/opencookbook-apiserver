package com.sterul.opencookbookapiserver.entities.seasonal;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One way a food comes to market in one month of one region. */
@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SeasonMonth {

    /** ISO 3166 country code: "DE". */
    @Column(nullable = false, length = 2)
    private String region;

    /** "month" is a reserved word in SQL. */
    @Column(name = "month_of_year", nullable = false)
    private short month;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GrowingMethod method;

    /** From least to most climate impact. */
    public enum GrowingMethod {
        FIELD,
        /** Under fleece or foil, unheated. */
        PROTECTED,
        /** Unheated or slightly heated. */
        UNHEATED_GREENHOUSE,
        HEATED_GREENHOUSE,
        STORED;

        /** Grown outdoors, which is what "in season" means. */
        public boolean isInSeason() {
            return this == FIELD || this == PROTECTED;
        }
    }
}
