package com.sterul.opencookbookapiserver.entities.nutrition;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Unique per language across all foods, ignoring case (enforced by the database). */
@Embeddable
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CatalogueFoodName {

    @Column(name = "language_iso_code", nullable = false)
    private String languageIsoCode;

    @Column(nullable = false)
    private String name;

    /** The shown name; one per language. */
    @Column(nullable = false)
    private boolean display;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Origin origin;

    public enum Origin {
        /** Replaced by the next dataset release. */
        DATASET,
        /** Kept across dataset releases. */
        ADMIN
    }

    /** An additional, not shown name added by an administrator. */
    public static CatalogueFoodName adminAlias(String languageIsoCode, String name) {
        return builder().languageIsoCode(languageIsoCode).name(name).display(false).origin(Origin.ADMIN).build();
    }

    public boolean sameAs(CatalogueFoodName other) {
        return languageIsoCode.equals(other.languageIsoCode) && name.trim().equalsIgnoreCase(other.name.trim());
    }
}
