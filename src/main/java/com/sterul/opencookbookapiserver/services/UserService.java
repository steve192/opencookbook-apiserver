package com.sterul.opencookbookapiserver.services;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sterul.opencookbookapiserver.configurations.OpencookbookConfiguration;
import com.sterul.opencookbookapiserver.entities.account.ActivationLink;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.entities.account.PasswordResetLink;
import com.sterul.opencookbookapiserver.entities.account.Role;
import com.sterul.opencookbookapiserver.repositories.ActivationLinkRepository;
import com.sterul.opencookbookapiserver.repositories.PasswordResetLinkRepository;
import com.sterul.opencookbookapiserver.repositories.UserRepository;
import com.sterul.opencookbookapiserver.services.exceptions.ElementNotFound;
import com.sterul.opencookbookapiserver.services.exceptions.InvalidActivationLinkException;
import com.sterul.opencookbookapiserver.services.exceptions.LastAdministratorException;
import com.sterul.opencookbookapiserver.services.exceptions.PasswordResetLinkNotExistingException;
import com.sterul.opencookbookapiserver.services.exceptions.SignupDisabledException;
import com.sterul.opencookbookapiserver.services.exceptions.UserAlreadyExistsException;
import com.sterul.opencookbookapiserver.services.households.HouseholdService;
import com.sterul.opencookbookapiserver.services.mail.MailLanguages;

import jakarta.mail.MessagingException;

import lombok.extern.slf4j.Slf4j;

@Service
@Transactional
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final IngredientService ingredientService;
    private final PasswordEncoder passwordEncoder;
    private final RecipeService recipeService;
    private final RecipeGroupService recipeGroupService;
    private final RecipeImageService recipeImageService;
    private final WeekplanService weekplanService;
    private final HouseholdService households;
    private final RefreshTokenService refreshTokenService;
    private final ActivationLinkRepository activationLinkRepository;
    private final PasswordResetLinkRepository passwordResetLinkRepository;
    private final EmailService emailService;
    private final OpencookbookConfiguration opencookbookConfiguration;
    private final MailLanguages mailLanguages;

    public UserService(UserRepository userRepository, IngredientService ingredientService,
            PasswordEncoder passwordEncoder, RecipeService recipeService, RecipeGroupService recipeGroupService,
            RecipeImageService recipeImageService, WeekplanService weekplanService, HouseholdService households,
            RefreshTokenService refreshTokenService, ActivationLinkRepository activationLinkRepository,
            PasswordResetLinkRepository passwordResetLinkRepository, EmailService emailService,
            OpencookbookConfiguration opencookbookConfiguration, MailLanguages mailLanguages) {
        this.userRepository = userRepository;
        this.ingredientService = ingredientService;
        this.passwordEncoder = passwordEncoder;
        this.recipeService = recipeService;
        this.recipeGroupService = recipeGroupService;
        this.recipeImageService = recipeImageService;
        this.weekplanService = weekplanService;
        this.households = households;
        this.refreshTokenService = refreshTokenService;
        this.activationLinkRepository = activationLinkRepository;
        this.passwordResetLinkRepository = passwordResetLinkRepository;
        this.emailService = emailService;
        this.opencookbookConfiguration = opencookbookConfiguration;
        this.mailLanguages = mailLanguages;
    }

    public CookpalUser getUserByEmail(String username) {
        return userRepository.findByEmailAddress(username);
    }

    /**
     * @param language the language to write to this account in, or null when the client did not
     *                 say - which leaves the account on the default rather than pinning it to a
     *                 guess it can never be talked out of
     */
    public com.sterul.opencookbookapiserver.entities.account.CookpalUser createUser(String emailAddress,
            String unencryptedPassword, Locale language) {
        if (!opencookbookConfiguration.isAllowSignup()) {
            throw new SignupDisabledException();
        }
        log.info("Creating user for {}", emailAddress);
        if (userExists(emailAddress)) {
            throw new UserAlreadyExistsException("User already exists");
        }
        var createdUser = new com.sterul.opencookbookapiserver.entities.account.CookpalUser();
        createdUser.setEmailAddress(emailAddress);
        createdUser.setPasswordHash(passwordEncoder.encode(unencryptedPassword));
        createdUser.setActivated(opencookbookConfiguration.isActivateUsersAfterSignup());
        createdUser.setLanguage(language == null ? null : language.getLanguage());
        createdUser = userRepository.save(createdUser);

        return createdUser;
    }

    /**
     * Keep the account's language in step with the client it is being used from, so that a mail
     * sent months later is still in the language the app is in.
     *
     * Called from the few places that already know who is asking - signing up, signing in,
     * renewing a token - rather than from a filter, and it writes only when the answer actually
     * changed, so a client that keeps saying the same thing costs nothing.
     */
    public void rememberLanguage(CookpalUser user, Locale language) {
        if (user == null || language == null || language.getLanguage().equals(user.getLanguage())) {
            return;
        }
        log.info("Language of user {} is now {}", user, language.getLanguage());
        user.setLanguage(language.getLanguage());
        userRepository.save(user);
    }

    /** What the client asking right now wants, if it said and if we have it. */
    public void rememberLanguageOfCurrentRequest(CookpalUser user) {
        mailLanguages.fromCurrentRequest().ifPresent(language -> rememberLanguage(user, language));
    }

    public boolean userExists(String emailAddress) {
        return userRepository.existsByEmailAddress(emailAddress);
    }

    public void deleteAllActivationLinks(CookpalUser user) {
        log.info("Deleting activation links for user {}", user);
        activationLinkRepository.deleteAllByUser(user);
    }

    public ActivationLink createActivationLink(CookpalUser user) {
        log.info("Creating activation link for user {}", user);
        deleteAllActivationLinks(user);
        activationLinkRepository.flush();

        var activationLink = new ActivationLink();
        activationLink.setUser(user);

        return activationLinkRepository.save(activationLink);
    }

    public CookpalUser setUserActivation(Long userId, boolean activated) {
        var user = getUserById(userId);
        requireAnAdministratorRemains(user, activated && user.getRoles() == Role.ADMIN);
        user.setActivated(activated);
        return userRepository.save(user);
    }

    /** Everything given replaces what was there, so a role of null takes the role away. */
    public CookpalUser updateUser(Long userId, String emailAddress, boolean activated, Role role) {
        var user = getUserById(userId);
        requireAnAdministratorRemains(user, activated && role == Role.ADMIN);

        if (!emailAddress.equalsIgnoreCase(user.getEmailAddress())) {
            if (userExists(emailAddress)) {
                throw new UserAlreadyExistsException("User already exists");
            }
            log.info("Changing the address of user {} to {}", userId, emailAddress);
            user.setEmailAddress(emailAddress);
        }

        user.setActivated(activated);
        user.setRoles(role);
        return userRepository.save(user);
    }

    /** Only the admin panel gives the role back, so losing the last one is final. */
    private void requireAnAdministratorRemains(CookpalUser user, boolean staysAnAdministrator) {
        if (staysAnAdministrator || user.getRoles() != Role.ADMIN || !user.isActivated()) {
            return;
        }
        if (userRepository.countByRolesAndActivated(Role.ADMIN, true) <= 1) {
            throw new LastAdministratorException();
        }
    }

    public CookpalUser activateUser(String activationId) {
        var activationLink = activationLinkRepository.findById(activationId);
        if (activationLink.isEmpty()) {
            throw new InvalidActivationLinkException();
        }
        log.info("Activating user {}", activationLink.get().getUser());
        var user = activationLink.get().getUser();
        user.setActivated(true);
        activationLinkRepository.delete(activationLink.get());
        return userRepository.save(user);
    }

    /** Blank clears it; the account then falls back to a masked address. */
    public CookpalUser setDisplayName(CookpalUser user, String displayName) {
        var tidied = displayName == null || displayName.isBlank() ? null : displayName.strip();
        log.info("Changing the display name of user {}", user.getUserId());
        user.setDisplayName(tidied);
        return userRepository.save(user);
    }

    public CookpalUser completeOnboarding(CookpalUser user, String displayName) {
        user.setOnboarded(true);
        return setDisplayName(user, displayName);
    }

    public void deleteUser(CookpalUser user) {
        requireAnAdministratorRemains(user, false);
        log.info("Deleting user {}", user);
        // Explicitly rather than by cascade, so the households hear of it.
        households.leaveAll(user);

        var recipes = recipeService.getRecipesByOwner(user);
        recipes.forEach(recipeService::deleteRecipe);

        var recipeGroups = recipeGroupService.getRecipeGroupsByOwner(user);
        recipeGroups.forEach(group -> recipeGroupService.deleteRecipeGroup(group.getId()));

        var images = recipeImageService.getImagesByUser(user);
        images.forEach(image -> {
            try {
                recipeImageService.deleteImage(image.getUuid());
            } catch (IOException e) {
                log.error("Error deleting image " + image.getUuid(), e);
            }
        });

        ingredientService.deleteAllIngredientsOfUser(user);

        var weekplanDays = weekplanService.getWeekplanDaysByOwner(user);
        weekplanDays.forEach(day -> weekplanService.deleteWeekplanDay(day.getId()));

        refreshTokenService.deleteAllRefreshTokenForUser(user);
        activationLinkRepository.deleteAllByUser(user);
        passwordResetLinkRepository.deleteAllByUser(user);

        userRepository.delete(user);
        try {
            emailService.sendAccountDeletedMail(user);
        } catch (MessagingException e) {
            log.error("Error sending account deletion mail to {}, ignoring", user);
        }
    }

    public boolean isPasswordCorrect(String emailAddress, String password) {
        var readUser = getUserByEmail(emailAddress);
        return passwordEncoder.matches(password, readUser.getPasswordHash());
    }

    public void changePassword(CookpalUser user, String newPassword) {
        log.info("Changing password for user {}", user);
        var readUser = getUserByEmail(user.getEmailAddress());
        readUser.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(readUser);
    }

    public void resendActivationLink(String emailAddress) throws MessagingException {
        log.info("Resending activation link for user {}", emailAddress);
        var user = getUserByEmail(emailAddress);

        if (user.isActivated()) {
            return;
        }
        var activationLink = createActivationLink(user);
        emailService.sendActivationMail(activationLink);
    }

    public void requestPasswordReset(String emailAddress) throws MessagingException {
        log.info("Requesting password reset for user {}", emailAddress);
        var user = getUserByEmail(emailAddress);

        var link = createPasswordResetLink(user);
        emailService.sendPasswordResetMail(link);
    }

    public PasswordResetLink createPasswordResetLink(CookpalUser user) {
        log.info("Creating password reset link for user {}", user);
        passwordResetLinkRepository.deleteAllByUser(user);
        // Flushed before the insert, as the activation links are: a user may hold only one reset
        // link, and without this Hibernate ordered the insert ahead of the delete within the
        // transaction - so asking for a second reset broke the unique constraint and 500ed.
        passwordResetLinkRepository.flush();

        var passwordResetLink = new PasswordResetLink();
        passwordResetLink.setUser(user);
        return passwordResetLinkRepository.save(passwordResetLink);
    }

    public void resetPassword(String newPassword, String passwordResetId) {

        var link = passwordResetLinkRepository.findById(passwordResetId);
        if (link.isEmpty()) {
            throw new PasswordResetLinkNotExistingException();
        }
        log.info("Resetting password for user {}", link.get().getUser());

        if (link.get().getValidUntil().isBefore(Instant.now())) {
            passwordResetLinkRepository.delete(link.get());
            throw new PasswordResetLinkNotExistingException();
        }

        var user = link.get().getUser();
        changePassword(user, newPassword);
        passwordResetLinkRepository.delete(link.get());
    }

    public List<CookpalUser> getAllUsers() {
        return userRepository.findAll();
    }

    /** One row of the operator's overview: an account and how much it holds. */
    public record UserHoldings(CookpalUser user, long recipeCount, long ingredientCount) {
    }

    /**
     * Two grouped queries for the whole instance, not a pair of counts per account. Only the
     * overview needs these numbers, so nothing else asks for them one account at a time.
     */
    public List<UserHoldings> getAllUserHoldings() {
        var recipeCounts = recipeService.countRecipesPerOwner();
        var ingredientCounts = ingredientService.countIngredientsPerOwner();

        return getAllUsers().stream()
                .map(user -> new UserHoldings(user,
                        countOf(recipeCounts, user),
                        countOf(ingredientCounts, user)))
                .toList();
    }

    private static long countOf(Map<Long, Long> counts, CookpalUser user) {
        return counts.getOrDefault(user.getUserId(), 0L);
    }

    public CookpalUser getUserById(Long id) {
        return findUserById(id).orElseThrow(ElementNotFound::new);
    }

    public Optional<CookpalUser> findUserById(Long id) {
        return userRepository.findById(id);
    }
}
