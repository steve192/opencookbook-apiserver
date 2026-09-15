package com.sterul.opencookbookapiserver.services.nutrition.matching;

/**
 * Evidence for and against a candidate, the input of {@link MatcherWeights}.
 *
 * @param queryCoverage        share of the typed name explained by the candidate, 0..1
 * @param candidateCoverage    share of the candidate's name explained by the typed name, 0..1
 * @param exact                1 if both names are the same word for word
 * @param fuzzyShare           share of the explanation resting on misspelt words, 0..1
 * @param unexplainedWords     typed words naming nothing known
 * @param conflictingFoodWords typed words naming another food ("Erdnuss" in "Erdnussbutter")
 * @param otherFoodWords       unexplained candidate words naming another food ("Butter" of "Buttermilch")
 * @param otherLanguage        1 if the matched name is in another language than the typed one
 * @param margin               distance to the next candidate; negative behind the best
 */
public record MatchFeatures(
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

    MatchFeatures withMargin(double newMargin) {
        return new MatchFeatures(queryCoverage, candidateCoverage, exact, fuzzyShare, unexplainedWords,
                conflictingFoodWords, otherFoodWords, missingStates, extraStates, otherLanguage, newMargin);
    }
}
