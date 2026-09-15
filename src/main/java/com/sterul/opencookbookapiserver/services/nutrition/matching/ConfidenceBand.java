package com.sterul.opencookbookapiserver.services.nutrition.matching;

/** What a calibrated confidence means for linking. */
public enum ConfidenceBand {
    /** Linked without asking. */
    SILENT(0.90),
    /** Linked, but flagged as uncertain. */
    UNCERTAIN(0.60),
    NONE(0);

    private final double from;

    ConfidenceBand(double from) {
        this.from = from;
    }

    public static ConfidenceBand of(double confidence) {
        if (confidence >= SILENT.from) {
            return SILENT;
        }
        return confidence >= UNCERTAIN.from ? UNCERTAIN : NONE;
    }

    public boolean links() {
        return this != NONE;
    }
}
