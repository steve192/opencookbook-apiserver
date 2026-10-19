package com.sterul.opencookbookapiserver.entities.seasonal;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** How a food keeps at home. A null place or a false flag means the sources did not agree. */
@Embeddable
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StorageFacts {

    @Enumerated(EnumType.STRING)
    @Column(name = "storage_place")
    private Place place;

    @Column(name = "storage_cold_sensitive", nullable = false)
    private boolean coldSensitive;

    @Column(name = "storage_ethylene_producer", nullable = false)
    private boolean ethyleneProducer;

    @Column(name = "storage_ethylene_sensitive", nullable = false)
    private boolean ethyleneSensitive;

    public enum Place {
        /** The vegetable drawer. */
        FRIDGE,
        /** Cool and dark, outside the fridge. */
        COOL_DARK
    }
}
