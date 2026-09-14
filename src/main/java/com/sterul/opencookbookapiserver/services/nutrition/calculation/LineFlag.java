package com.sterul.opencookbookapiserver.services.nutrition.calculation;

/** Why a resolved line is less certain than it looks. */
public enum LineFlag {
    /** Automatic link below the silent band. */
    LOW_CONFIDENCE,
    /** Volume weighed as water. */
    VOLUME_WITHOUT_DENSITY,
    ESTIMATED_PORTION,
    /** Small or large piece scaled from an ordinary one. */
    SIZE_SCALED_PORTION,
    /** Container weighed by its typical content ("1 Dose" = 400 g). */
    TYPICAL_CONTAINER_SIZE,
    PINCH
}
