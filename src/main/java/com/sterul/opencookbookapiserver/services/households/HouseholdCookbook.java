package com.sterul.opencookbookapiserver.services.households;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.entities.recipe.Recipe;
import com.sterul.opencookbookapiserver.repositories.RecipeRepository;
import com.sterul.opencookbookapiserver.repositories.projections.RecipeTitle;
import com.sterul.opencookbookapiserver.services.selection.FuzzyTitleSearch;

import jakarta.annotation.Nullable;

/** A household's cookbook: the recipes of its members who share theirs. Only its members may look. */
@Service
@Transactional(readOnly = true)
public class HouseholdCookbook {

    public static final int PAGE_SIZE = 50;

    private final RecipeRepository recipeRepository;
    private final HouseholdMembershipService memberships;

    public HouseholdCookbook(RecipeRepository recipeRepository, HouseholdMembershipService memberships) {
        this.recipeRepository = recipeRepository;
        this.memberships = memberships;
    }

    /**
     * @param page   counted from zero
     * @param search blank for everything by title, otherwise the best matches first
     */
    public Slice<Recipe> recipesOf(String householdId, CookpalUser viewer, int page, @Nullable String search) {
        memberships.requireMembership(householdId, viewer);
        var sharingMembers = memberships.sharingMemberIdsOf(householdId);
        var request = PageRequest.of(page, PAGE_SIZE);
        if (sharingMembers.isEmpty()) {
            return new SliceImpl<>(List.of(), request, false);
        }
        if (search == null || search.isBlank()) {
            return recipeRepository.findByOwnerUserIdInOrderByTitleAscIdAsc(sharingMembers, request);
        }
        return matching(search.strip(), sharingMembers, request);
    }

    public long recipeCountOf(String householdId, CookpalUser viewer) {
        memberships.requireMembership(householdId, viewer);
        var sharingMembers = memberships.sharingMemberIdsOf(householdId);
        return sharingMembers.isEmpty() ? 0 : recipeRepository.countByOwnerUserIdIn(sharingMembers);
    }

    /** Matched on titles alone, so only the recipes on the requested page are loaded. */
    private Slice<Recipe> matching(String search, Set<Long> owners, Pageable request) {
        var matches = FuzzyTitleSearch.filter(search, recipeRepository.findTitlesByOwners(owners), RecipeTitle::title);
        var from = (int) Math.min(request.getOffset(), matches.size());
        var to = Math.min(from + request.getPageSize(), matches.size());
        var ids = matches.subList(from, to).stream().map(RecipeTitle::id).toList();

        var byId = recipeRepository.findByIdInAndOwnerUserIdIn(ids, owners).stream()
                .collect(Collectors.toMap(Recipe::getId, Function.identity()));
        // In match order; a recipe deleted since its title was read is left out.
        var recipes = ids.stream().map(byId::get).filter(Objects::nonNull).toList();
        return new SliceImpl<>(recipes, request, to < matches.size());
    }
}
