package com.sterul.opencookbookapiserver.services.suggestion;

import java.util.List;

import org.springframework.stereotype.Service;

import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.services.selection.IngredientTargetResolver;

import jakarta.transaction.Transactional;

/** Suggests recipes from a cook's own cookbook: resolve what they named, narrow, rank. */
@Service
@Transactional
public class RecipeSuggestionService {

    /** Enough to show the band is there without competing with the results above it. */
    private static final int NEAR_MISS_LIMIT = 5;

    private final IngredientTargetResolver targetResolver;
    private final CandidatePool candidatePool;
    private final CandidateRanker ranker;

    public RecipeSuggestionService(IngredientTargetResolver targetResolver, CandidatePool candidatePool,
            CandidateRanker ranker) {
        this.targetResolver = targetResolver;
        this.candidatePool = candidatePool;
        this.ranker = ranker;
    }

    public SuggestionResult suggest(SuggestionCriteria criteria, CookpalUser owner) {
        var targets = targetResolver.resolve(criteria.ingredientIds(), owner);
        var pool = candidatePool.of(owner, targets, criteria);

        var results = firstOf(ranker.rank(pool.hits(), criteria), criteria.limit());
        var nearMisses = firstOf(ranker.rank(pool.nearMisses(), criteria), NEAR_MISS_LIMIT);

        return new SuggestionResult(criteria.seed(), results, nearMisses,
                new SuggestionResult.PoolStats(pool.owned(), pool.afterFilters(), pool.hits().size(), results.size()));
    }

    private static List<RankedSuggestion> firstOf(List<RankedSuggestion> ranked, int limit) {
        return ranked.stream().limit(limit).toList();
    }
}
