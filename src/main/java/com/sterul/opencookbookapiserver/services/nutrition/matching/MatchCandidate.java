package com.sterul.opencookbookapiserver.services.nutrition.matching;

/**
 * @param foodKey    catalogue key; a variant where the typed name asks for its state
 * @param confidence probability that this is the food meant
 */
public record MatchCandidate(String foodKey, String matchedName, double confidence, MatchFeatures features) {

    public ConfidenceBand band() {
        return ConfidenceBand.of(confidence);
    }
}
