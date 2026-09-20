package com.sterul.opencookbookapiserver.entities.planning;

public enum SlotKind {
    /** A recipe cooked for this meal. */
    COOKED,
    /** What is left of a recipe cooked earlier; nothing to cook. */
    LEFTOVER,
    /** Deliberately left to the cook - "Butterbrot mit Käse" is in nobody's cookbook. */
    GAP
}
