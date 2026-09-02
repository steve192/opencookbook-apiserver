package com.sterul.opencookbookapiserver.services.sharing;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sterul.opencookbookapiserver.configurations.OpencookbookConfiguration;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.entities.recipe.Recipe;
import com.sterul.opencookbookapiserver.entities.sharing.Share;
import com.sterul.opencookbookapiserver.entities.sharing.ShareResourceType;
import com.sterul.opencookbookapiserver.entities.sharing.ShareVisibility;
import com.sterul.opencookbookapiserver.repositories.ShareRepository;
import com.sterul.opencookbookapiserver.services.RecipeService;
import com.sterul.opencookbookapiserver.services.exceptions.ElementNotFound;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@Transactional
public class ShareService {

    private final ShareRepository shareRepository;
    private final RecipeService recipeService;
    private final OpencookbookConfiguration configuration;
    private final Clock clock;

    public ShareService(ShareRepository shareRepository, RecipeService recipeService,
            OpencookbookConfiguration configuration, Clock clock) {
        this.shareRepository = shareRepository;
        this.recipeService = recipeService;
        this.configuration = configuration;
        this.clock = clock;
    }

    public Share shareRecipePublicly(Long recipeId) throws ElementNotFound {
        var recipe = recipeService.getRecipeForUpdate(recipeId);

        var existingShare = publicShareOf(recipe);
        if (existingShare.isPresent()) {
            if (!existingShare.get().hasExpired(clock.instant())) {
                return existingShare.get();
            }
            // A lapsed link is not renewed in place - it gets replaced. Reusing the id would
            // quietly bring a link back to life for everyone it was ever sent to, years later,
            // which is the opposite of what an expiry is for.
            log.info("Replacing lapsed share {} of recipe {}", existingShare.get().getId(), recipeId);
            shareRepository.delete(existingShare.get());
            shareRepository.flush();
        }

        var owner = recipe.getOwner();
        log.info("Creating public share for recipe {} of user {}", recipeId, owner.getUserId());
        return shareRepository.save(Share.builder()
                .owner(owner)
                .resourceType(ShareResourceType.RECIPE)
                .visibility(ShareVisibility.PUBLIC_LINK)
                .recipe(recipe)
                .expiresAt(expiryForNewShare())
                .accessCount(0)
                .build());
    }

    @Transactional(readOnly = true)
    public Optional<Share> findPublicRecipeShare(Long recipeId) throws ElementNotFound {
        return livePublicShareOf(recipeService.getRecipeById(recipeId));
    }

    @Transactional(readOnly = true)
    public Share resolveLiveShare(String shareId) throws ElementNotFound {
        return shareRepository.findById(shareId)
                .filter(share -> !share.hasExpired(clock.instant()))
                .orElseThrow(ElementNotFound::new);
    }

    @Transactional(readOnly = true)
    public Recipe resolveSharedRecipe(String shareId) throws ElementNotFound {
        return recipeService.getRecipeById(resolveLiveShare(shareId).getRecipe().getId());
    }

    public Recipe openSharedRecipe(String shareId) throws ElementNotFound {
        var recipe = resolveSharedRecipe(shareId);
        shareRepository.incrementAccessCount(shareId);
        return recipe;
    }

    @Transactional(readOnly = true)
    public void requireSharedImage(String shareId, String imageUuid) throws ElementNotFound {
        var shareShowsImage = resolveSharedRecipe(shareId).getImages().stream()
                .anyMatch(image -> image.getUuid().equals(imageUuid));
        if (!shareShowsImage) {
            throw new ElementNotFound();
        }
    }

    public void revoke(String shareId, CookpalUser requester) throws ElementNotFound {
        var share = shareRepository.findById(shareId)
                .filter(candidate -> candidate.getOwner().getUserId().equals(requester.getUserId()))
                .orElseThrow(ElementNotFound::new);
        log.info("Revoking share {} of user {}", shareId, requester.getUserId());
        shareRepository.delete(share);
    }

    public void revokeAllSharesOfRecipe(Recipe recipe) {
        shareRepository.deleteByRecipe(recipe);
    }

    public int deleteExpiredShares() {
        return shareRepository.deleteByExpiresAtBefore(clock.instant());
    }

    @Transactional(readOnly = true)
    public List<Share> getAllShares() {
        return shareRepository.findAllForAdministration();
    }

    public void revokeAsAdministrator(String shareId) throws ElementNotFound {
        var share = shareRepository.findById(shareId).orElseThrow(ElementNotFound::new);
        log.info("Administrator is revoking share {} of user {}", shareId, share.getOwner().getUserId());
        shareRepository.delete(share);
    }

    @Transactional(readOnly = true)
    public ShareStatistics getStatistics(Duration expiringSoonWindow) {
        return new ShareStatistics(
                shareRepository.count(),
                shareRepository.sumAccessCount(),
                shareRepository.countByExpiresAtBefore(clock.instant().plus(expiringSoonWindow)));
    }

    public record ShareStatistics(long totalShares, long totalAccesses, long expiringSoon) {
    }

    private Optional<Share> livePublicShareOf(Recipe recipe) {
        return publicShareOf(recipe).filter(share -> !share.hasExpired(clock.instant()));
    }

    private Optional<Share> publicShareOf(Recipe recipe) {
        return shareRepository.findByRecipeAndVisibility(recipe, ShareVisibility.PUBLIC_LINK);
    }

    private Instant expiryForNewShare() {
        return clock.instant().plus(Duration.ofDays(configuration.getSharing().getValidityDays()));
    }
}
