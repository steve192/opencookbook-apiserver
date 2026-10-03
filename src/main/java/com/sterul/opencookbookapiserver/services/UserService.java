package com.sterul.opencookbookapiserver.services;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sterul.opencookbookapiserver.entities.account.ActivationLink;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.entities.account.PasswordResetLink;
import com.sterul.opencookbookapiserver.entities.account.Role;
import com.sterul.opencookbookapiserver.entities.shopping.ShoppingProvider;
import com.sterul.opencookbookapiserver.errors.ApiErrorCode;
import com.sterul.opencookbookapiserver.repositories.ActivationLinkRepository;
import com.sterul.opencookbookapiserver.repositories.PasswordResetLinkRepository;
import com.sterul.opencookbookapiserver.repositories.UserRepository;
import com.sterul.opencookbookapiserver.services.exceptions.ElementNotFound;
import com.sterul.opencookbookapiserver.services.exceptions.InvalidActivationLinkException;
import com.sterul.opencookbookapiserver.services.exceptions.PasswordResetLinkNotExistingException;
import com.sterul.opencookbookapiserver.services.exceptions.UserAlreadyExistsException;
import com.sterul.opencookbookapiserver.services.households.HouseholdService;
import com.sterul.opencookbookapiserver.services.instance.Administrators;
import com.sterul.opencookbookapiserver.services.mail.MailAvailability;
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
    private final SignInService signInService;
    private final ActivationLinkRepository activationLinkRepository;
    private final PasswordResetLinkRepository passwordResetLinkRepository;
    private final EmailService emailService;
    private final MailAvailability mail;
    private final AccountLinkFactory accountLinks;
    private final MailLanguages mailLanguages;
    private final Administrators administrators;
    private final AuthRateLimiter authRateLimiter;
    private final ApplicationEventPublisher events;

    public UserService(UserRepository userRepository, IngredientService ingredientService,
            PasswordEncoder passwordEncoder, RecipeService recipeService, RecipeGroupService recipeGroupService,
            RecipeImageService recipeImageService, WeekplanService weekplanService, HouseholdService households,
            SignInService signInService, ActivationLinkRepository activationLinkRepository,
            PasswordResetLinkRepository passwordResetLinkRepository, EmailService emailService,
            MailAvailability mail, AccountLinkFactory accountLinks, MailLanguages mailLanguages,
            Administrators administrators, AuthRateLimiter authRateLimiter, ApplicationEventPublisher events) {
        this.userRepository = userRepository;
        this.ingredientService = ingredientService;
        this.passwordEncoder = passwordEncoder;
        this.recipeService = recipeService;
        this.recipeGroupService = recipeGroupService;
        this.recipeImageService = recipeImageService;
        this.weekplanService = weekplanService;
        this.households = households;
        this.signInService = signInService;
        this.activationLinkRepository = activationLinkRepository;
        this.passwordResetLinkRepository = passwordResetLinkRepository;
        this.emailService = emailService;
        this.mail = mail;
        this.accountLinks = accountLinks;
        this.mailLanguages = mailLanguages;
        this.administrators = administrators;
        this.authRateLimiter = authRateLimiter;
        this.events = events;
    }

    public CookpalUser getUserByEmail(String username) {
        return userRepository.findByEmailAddress(username);
    }

    /**
     * Whether somebody may create an account is decided by the callers: the setup and signing up.
     *
     * The language is whatever the client asked for in Accept-Language, the only thing known about
     * a person who does not have an account yet. When it said nothing the account stays on the
     * default rather than being pinned to a guess it can never be talked out of.
     *
     * @param role null for an ordinary account
     */
    public CookpalUser createUser(String emailAddress, String unencryptedPassword, boolean activated, Role role) {
        log.info("Creating user for {}", emailAddress);
        if (userExists(emailAddress)) {
            throw new UserAlreadyExistsException("User already exists");
        }
        var createdUser = new CookpalUser();
        createdUser.setEmailAddress(emailAddress);
        createdUser.setPasswordHash(passwordEncoder.encode(unencryptedPassword));
        createdUser.setActivated(activated);
        createdUser.setRoles(role);
        createdUser.setLanguage(mailLanguages.fromCurrentRequest().map(Locale::getLanguage).orElse(null));
        return userRepository.save(createdUser);
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
        administrators.requireOneRemains(user, activated && user.getRoles() == Role.ADMIN);
        applyActivation(user, activated);
        return userRepository.save(user);
    }

    /** Everything given replaces what was there, so a role of null takes the role away. */
    public CookpalUser updateUser(Long userId, String emailAddress, boolean activated, Role role) {
        var user = getUserById(userId);
        administrators.requireOneRemains(user, activated && role == Role.ADMIN);

        if (!emailAddress.equalsIgnoreCase(user.getEmailAddress())) {
            if (userExists(emailAddress)) {
                throw new UserAlreadyExistsException("User already exists");
            }
            log.info("Changing the address of user {} to {}", userId, emailAddress);
            user.setEmailAddress(emailAddress);
            // Links were sent to the old address; whoever owns it must not be able to use them.
            activationLinkRepository.deleteAllByUser(user);
            passwordResetLinkRepository.deleteAllByUser(user);
        }

        // Saving the form must not take the confirmation link from an account that still waits for it.
        if (user.isActivated() != activated) {
            applyActivation(user, activated);
        }
        user.setRoles(role);
        return userRepository.save(user);
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

    public CookpalUser setShoppingProvider(CookpalUser user, ShoppingProvider provider) {
        user.setShoppingProvider(provider);
        return userRepository.save(user);
    }

    public CookpalUser completeOnboarding(CookpalUser user, String displayName) {
        user.setOnboarded(true);
        return setDisplayName(user, displayName);
    }

    public void deleteUser(CookpalUser user) {
        administrators.requireOneRemains(user, false);
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

        signInService.endAll(user);
        activationLinkRepository.deleteAllByUser(user);
        passwordResetLinkRepository.deleteAllByUser(user);

        userRepository.delete(user);
        events.publishEvent(new AccountDeletedEvent(user.getEmailAddress(), mailLanguages.forUser(user)));
    }

    public boolean isPasswordCorrect(String emailAddress, String password) {
        var readUser = getUserByEmail(emailAddress);
        return passwordEncoder.matches(password, readUser.getPasswordHash());
    }

    /**
     * Every other sign in ends, as the reason to change a password is often that somebody else knows it.
     *
     * @param keptSessionId the sign in the password was changed in
     */
    public void changePassword(CookpalUser user, String newPassword, String keptSessionId) {
        var changed = setPassword(user, newPassword);
        signInService.keepOnly(changed, keptSessionId);
    }

    private CookpalUser setPassword(CookpalUser user, String newPassword) {
        log.info("Changing password for user {}", user);
        var readUser = getUserByEmail(user.getEmailAddress());
        readUser.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(readUser);
        return readUser;
    }

    /**
     * An administrator's decision replaces a confirmation that was still pending, so a locked
     * account cannot unlock itself through its link. A deactivated account keeps no sign in; its
     * access tokens are refused as well.
     */
    private void applyActivation(CookpalUser user, boolean activated) {
        user.setActivated(activated);
        activationLinkRepository.deleteAllByUser(user);
        if (!activated) {
            signInService.endAll(user);
        }
    }

    /**
     * Mails the activation link again to an account that waits for its address to be confirmed.
     * Every other address is answered the same way, so this cannot tell which ones are registered.
     */
    public void resendActivationLink(String emailAddress) throws MessagingException {
        var user = getUserByEmail(emailAddress);
        if (user != null && awaitsConfirmation(user)) {
            mailActivationLink(user);
        }
    }

    /**
     * Why somebody who proved their password still cannot sign in. An account waiting for its
     * confirmation gets the link again on the way, as they have lost it or never received it; a
     * mail server that is down must not turn that into a different answer than the one they need.
     */
    public ApiErrorCode explainLockedAccount(String emailAddress) {
        var user = getUserByEmail(emailAddress);
        if (!awaitsConfirmation(user)) {
            return ApiErrorCode.ACCOUNT_AWAITING_APPROVAL;
        }
        try {
            mailActivationLink(user);
        } catch (MessagingException e) {
            log.error("Error re-sending activation link for user {}", user, e);
        }
        return ApiErrorCode.ACCOUNT_NOT_ACTIVATED;
    }

    /** Otherwise the account is locked until an administrator unlocks it. */
    private boolean awaitsConfirmation(CookpalUser user) {
        return mail.isEnabled() && !user.isActivated() && activationLinkRepository.existsByUser(user);
    }

    private void mailActivationLink(CookpalUser user) throws MessagingException {
        if (mayMail(user.getEmailAddress())) {
            log.info("Resending activation link for user {}", user);
            emailService.sendActivationMail(createActivationLink(user));
        }
    }

    /** Answers the same whether or not an address belongs to an account, except for an instance that cannot mail. */
    public void requestPasswordReset(String emailAddress) throws MessagingException {
        mail.requireEnabled();
        var user = getUserByEmail(emailAddress);
        if (user != null && mayMail(emailAddress)) {
            log.info("Requesting password reset for user {}", emailAddress);
            emailService.sendPasswordResetMail(createPasswordResetLink(user));
        }
    }

    /**
     * Whether one more mail may be sent to an address. A spent allowance is not reported: the
     * callers answer the same whether or not an account exists.
     */
    private boolean mayMail(String emailAddress) {
        if (authRateLimiter.mayMail(emailAddress)) {
            return true;
        }
        log.warn("Not sending another mail to a recipient who has had their hourly allowance");
        return false;
    }

    /** A reset link for an administrator to hand over. */
    public record HandedOverPasswordReset(String link, boolean mailed) {
    }

    /** The same link as a reset asked for at the login screen, and mailed as well when this instance can. */
    public HandedOverPasswordReset createPasswordResetForAdministrator(Long userId) {
        var link = createPasswordResetLink(getUserById(userId));
        return new HandedOverPasswordReset(accountLinks.passwordReset(link), mailIfPossible(link));
    }

    /** A mail server that is down must not hide the link: the administrator can still hand it over. */
    private boolean mailIfPossible(PasswordResetLink link) {
        if (!mail.isEnabled()) {
            return false;
        }
        try {
            emailService.sendPasswordResetMail(link);
            return true;
        } catch (MessagingException e) {
            log.warn("Could not mail the password reset link to user {}", link.getUser(), e);
            return false;
        }
    }

    private PasswordResetLink createPasswordResetLink(CookpalUser user) {
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

        var changed = setPassword(link.get().getUser(), newPassword);
        signInService.endAll(changed);
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
