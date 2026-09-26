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

    public Share shareRecipePublicly(Long recipeId) {
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
    public Optional<Share> findPublicRecipeShare(Long recipeId) {
        return livePublicShareOf(recipeService.getRecipeIgnoringAccess(recipeId));
    }

    /**
     * The shared recipe and the live share are wanted both on their own, where the
     * annotated entry points below apply, and from the writing methods, which must not run
     * them as a separate read-only transaction. Calling an annotated method through
     * {@code this} would not do that anyway, since the proxy is not involved.
     */
    private Share liveShare(String shareId) {
        return shareRepository.findById(shareId)
                .filter(share -> !share.hasExpired(clock.instant()))
                .orElseThrow(ElementNotFound::new);
    }

    private Recipe sharedRecipe(String shareId) {
        return recipeService.getRecipeIgnoringAccess(liveShare(shareId).getRecipe().getId());
    }

    @Transactional(readOnly = true)
    public Share resolveLiveShare(String shareId) {
        return liveShare(shareId);
    }

    @Transactional(readOnly = true)
    public Recipe resolveSharedRecipe(String shareId) {
        return sharedRecipe(shareId);
    }

    public Recipe openSharedRecipe(String shareId) {
        var recipe = sharedRecipe(shareId);
        shareRepository.incrementAccessCount(shareId);
        return recipe;
    }

    @Transactional(readOnly = true)
    public void requireSharedImage(String shareId, String imageUuid) {
        var shareShowsImage = sharedRecipe(shareId).getImages().stream()
                .anyMatch(image -> image.getUuid().equals(imageUuid));
        if (!shareShowsImage) {
            throw new ElementNotFound();
        }
    }

    public void revoke(String shareId, CookpalUser requester) {
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

    public void revokeAsAdministrator(String shareId) {
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
