package com.sterul.opencookbookapiserver.services;

import java.io.IOException;

import com.sterul.opencookbookapiserver.entities.account.ActivationLink;
import com.sterul.opencookbookapiserver.entities.account.User;
import com.sterul.opencookbookapiserver.repositories.ActivationLinkRepository;
import com.sterul.opencookbookapiserver.repositories.UserRepository;
import com.sterul.opencookbookapiserver.services.exceptions.InvalidActivationLinkException;
import com.sterul.opencookbookapiserver.services.exceptions.UserAlreadyExistsException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class UserService {

    @Autowired
    private UserRepository userRepository;

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
    private EmailService emailService;

    public User getUserByEmail(String username) {
        return userRepository.findByEmailAddress(username);
    }

    public com.sterul.opencookbookapiserver.entities.account.User createUser(String emailAddress,
            String unencryptedPassword) throws UserAlreadyExistsException {
        if (userExists(emailAddress)) {
            throw new UserAlreadyExistsException("User already exists");
        }
        var createdUser = new com.sterul.opencookbookapiserver.entities.account.User();
        createdUser.setEmailAddress(emailAddress);
        createdUser.setPasswordHash(passwordEncoder.encode(unencryptedPassword));
        createdUser.setActivated(false);
        createdUser = userRepository.save(createdUser);

        return createdUser;
    }

    public boolean userExists(String emailAddress) {
        return userRepository.existsByEmailAddress(emailAddress);
    }

    public void deleteAllActivationLinks(User user) {
        activationLinkRepository.deleteByUser(user);
    }

    public ActivationLink createActivationLink(User user) {
        deleteAllActivationLinks(user);

        var activationLink = new ActivationLink();
        activationLink.setUser(user);

        return activationLinkRepository.save(activationLink);
    }

    public void activateUser(String activationId) throws InvalidActivationLinkException {
        var activationLink = activationLinkRepository.findById(activationId);
        if (activationLink.isEmpty()) {
            throw new InvalidActivationLinkException();
        }
        var user = activationLink.get().getUser();
        user.setActivated(true);
        activationLinkRepository.delete(activationLink.get());
        userRepository.save(user);
    }

    public void deleteUser(User user) {
        var recipes = recipeService.getRecipesByOwner(user);
        recipes.stream().forEach(recipe -> recipeService.deleteRecipe(recipe.getId()));

        var recipeGroups = recipeGroupService.getRecipeGroupsByOwner(user);
        recipeGroups.stream().forEach(group -> recipeGroupService.deleteRecipeGroup(group.getId()));

        var images = recipeImageService.getImagesByUser(user);
        images.stream().forEach(image -> {
            try {
                recipeImageService.deleteImage(image.getUuid());
            } catch (IOException e) {
                log.error("Error deleting image " + image.getUuid(), e);
            }
        });

        var weekplanDays = weekplanService.getWeekplanDaysByOwner(user);
        weekplanDays.stream().forEach(day -> weekplanService.deleteWeekplanDay(day.getId()));

        refreshTokenService.deleteAllRefreshTokenForUser(user);

        userRepository.delete(user);
    }
}
