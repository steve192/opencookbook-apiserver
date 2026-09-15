package com.sterul.opencookbookapiserver.services.nutrition.calculation;

public enum LineStatus {
    RESOLVED,
    /** Declared as not counting for nutrition, e.g. a toothpick. */
    EXCLUDED,
    UNLINKED,
    UNIT_UNKNOWN,
    /** Pieces of a food without a known piece weight. */
    NO_PORTION,
    /** No or only a vague amount ("etwas"). */
    NO_AMOUNT
}
