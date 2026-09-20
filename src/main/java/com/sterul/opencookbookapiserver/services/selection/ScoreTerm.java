package com.sterul.opencookbookapiserver.services.selection;

import java.util.List;

/**
 * One signed contribution to a candidate's rank. The term is a stable key, not a sentence: the
 * client turns it into text in the reader's language.
 */
public record ScoreTerm(String term, double value) {

    public static double total(List<ScoreTerm> terms) {
        return terms.stream().mapToDouble(ScoreTerm::value).sum();
    }
}
