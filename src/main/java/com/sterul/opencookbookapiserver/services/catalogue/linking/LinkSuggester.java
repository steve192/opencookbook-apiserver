package com.sterul.opencookbookapiserver.services.catalogue.linking;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.sterul.opencookbookapiserver.services.catalogue.matching.CatalogueMatcher;
import com.sterul.opencookbookapiserver.services.catalogue.matching.MatchCandidate;

/** The most likely food the name rules allow, if confident enough to link. */
@Component
public class LinkSuggester {

    /** Candidates to look at when rules forbid the first ones. */
    private static final int CANDIDATES = 5;

    private final CatalogueMatcher matcher;

    public LinkSuggester(CatalogueMatcher matcher) {
        this.matcher = matcher;
    }

    public boolean isReady() {
        return matcher.isReady();
    }

    public void requireReady() {
        matcher.requireReady();
    }

    public Optional<MatchCandidate> suggest(String name, String language, RuleBook rules) {
        if (rules.isNotAFood(name)) {
            return Optional.empty();
        }
        return matcher.rank(name, language, CANDIDATES).stream()
                .filter(candidate -> !rules.forbids(name, candidate.foodKey()))
                .findFirst()
                .filter(candidate -> candidate.band().links());
    }
}
