package com.sterul.opencookbookapiserver.services.nutrition.matching;

import com.sterul.opencookbookapiserver.services.nutrition.dataset.NutritionDatasetReader;

/** Logistic model turning {@link MatchFeatures} into a confidence; fitted by CatalogueMatcherCalibrationTest. */
public record MatcherWeights(
        double bias,
        double queryCoverage,
        double candidateCoverage,
        double exact,
        double fuzzyShare,
        double unexplainedWords,
        double conflictingFoodWords,
        double otherFoodWords,
        double missingStates,
        double extraStates,
        double otherLanguage,
        double margin) {

    /** Shipped next to the dataset, but not covered by its checksum. */
    public static MatcherWeights shipped(NutritionDatasetReader reader) {
        return reader.read("matcher-weights.json", MatcherWeights.class);
    }

    /** Log-odds before the margin to other candidates is known. */
    double logOddsWithoutMargin(MatchFeatures features) {
        return bias
                + queryCoverage * features.queryCoverage()
                + candidateCoverage * features.candidateCoverage()
                + exact * features.exact()
                + fuzzyShare * features.fuzzyShare()
                + unexplainedWords * features.unexplainedWords()
                + conflictingFoodWords * features.conflictingFoodWords()
                + otherFoodWords * features.otherFoodWords()
                + missingStates * features.missingStates()
                + extraStates * features.extraStates()
                + otherLanguage * features.otherLanguage();
    }

    double confidence(MatchFeatures features) {
        return logistic(logOddsWithoutMargin(features) + margin * features.margin());
    }

    static double logistic(double logOdds) {
        return 1 / (1 + Math.exp(-logOdds));
    }
}
