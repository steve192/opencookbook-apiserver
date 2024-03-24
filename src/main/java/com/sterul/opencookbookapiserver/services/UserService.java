package com.sterul.opencookbookapiserver.services;

import java.io.IOException;
import java.util.Date;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
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
import com.sterul.opencookbookapiserver.services.exceptions.PasswordResetLinkNotExistingException;
import com.sterul.opencookbookapiserver.services.exceptions.SignupDisabledException;
import com.sterul.opencookbookapiserver.services.exceptions.UserAlreadyExistsException;

import jakarta.mail.MessagingException;
import lombok.extern.slf4j.Slf4j;

@Service
@Transactional
@Slf4j
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private IngredientService ingredientService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private RecipeService recipeService;

    @Autowired
    private RecipeGroupService recipeGroupService;

    @Autowired
    private RecipeImageService recipeImageService;

    @Autowired
    private WeekplanService weekplanService;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private ActivationLinkRepository activationLinkRepository;

    @Autowired
    private PasswordResetLinkRepository passwordResetLinkRepository;

    @Autowired
    private EmailService emailService;

    @Autowired
    private OpencookbookConfiguration opencookbookConfiguration;

    public CookpalUser getUserByEmail(String username) {
        return userRepository.findByEmailAddress(username);
    }

    public com.sterul.opencookbookapiserver.entities.account.CookpalUser createUser(String emailAddress,
            String unencryptedPassword) throws UserAlreadyExistsException, SignupDisabledException {
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
        createdUser = userRepository.save(createdUser);

        return createdUser;
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

        var activationLink = new ActivationLink();
        activationLink.setUser(user);

        return activationLinkRepository.save(activationLink);
    }

    public void activateUserById(Long userId) throws ElementNotFound {
        var user = userRepository.findById(userId).orElseThrow(ElementNotFound::new);
        user.setActivated(true);
        userRepository.save(user);
    }

    public CookpalUser activateUser(String activationId) throws InvalidActivationLinkException {
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

    public void deleteUser(CookpalUser user) {
        log.info("Deleting user {}", user);
        var recipes = recipeService.getRecipesByOwner(user);
        recipes.forEach(recipe -> recipeService.deleteRecipe(recipe.getId()));

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
        deleteAllActivationLinks(user);
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
        var passwordResetLink = new PasswordResetLink();
        passwordResetLink.setUser(user);
        return passwordResetLinkRepository.save(passwordResetLink);
    }

    public void resetPassword(String newPassword, String passwordResetId) throws PasswordResetLinkNotExistingException {

        var link = passwordResetLinkRepository.findById(passwordResetId);
        if (link.isEmpty()) {
            throw new PasswordResetLinkNotExistingException();
        }
        log.info("Resetting password for user {}", link.get().getUser());

        if (link.get().getValidUntil().before(new Date())) {
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

    public CookpalUser getUserById(Long id) throws ElementNotFound {
        return userRepository.findById(id).orElseThrow(ElementNotFound::new);
    }

    public CookpalUser updateUserRoles(Long id, List<Role> roles) throws ElementNotFound {
        var user = getUserById(id);
        // Change if multiple roles are supported some time
        user.setRoles(roles.get(0));
        return userRepository.save(user);
    }
}
