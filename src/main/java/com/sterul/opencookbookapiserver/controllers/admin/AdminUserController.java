package com.sterul.opencookbookapiserver.controllers.admin;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.sterul.opencookbookapiserver.controllers.admin.requests.AdminUserRequest;
import com.sterul.opencookbookapiserver.controllers.admin.responses.AdminUserOverviewResponse;
import com.sterul.opencookbookapiserver.controllers.admin.responses.AdminUserResponse;
import com.sterul.opencookbookapiserver.services.UserService;
import com.sterul.opencookbookapiserver.services.exceptions.ElementNotFound;
import com.sterul.opencookbookapiserver.services.exceptions.LastAdministratorException;
import com.sterul.opencookbookapiserver.services.exceptions.UserAlreadyExistsException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.mail.MessagingException;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/v1/admin/users")
@Tag(name = "Users", description = "Users admin api")
@Slf4j
public class AdminUserController {

    private final UserService userService;

    public AdminUserController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "Every account on this instance, with how much each one holds")
    @GetMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public List<AdminUserOverviewResponse> getAll() {
        log.info("Admin: Accessing all users");
        return userService.getAllUserHoldings().stream()
                .map(AdminUserOverviewResponse::fromHoldings)
                .toList();
    }

    @Operation(summary = "One account")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public AdminUserResponse getOne(@PathVariable Long id) throws ElementNotFound {
        return AdminUserResponse.fromEntity(userService.getUserById(id));
    }

    @Operation(summary = "Change an account's address, activation and role",
            description = "Everything given replaces what was there, so leaving the role out "
                    + "takes it away. The last administrator who can sign in cannot be changed "
                    + "into somebody who cannot.")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public AdminUserResponse updateUser(@PathVariable Long id, @Valid @RequestBody AdminUserRequest request)
            throws ElementNotFound, UserAlreadyExistsException, LastAdministratorException {
        log.info("Admin: Updating user {}", id);
        return AdminUserResponse.fromEntity(userService.updateUser(id, request.getEmailAddress(),
                request.getActivated(), request.getRole()));
    }

    @Operation(summary = "Delete an account and everything it holds")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteUser(@PathVariable Long id) throws ElementNotFound, LastAdministratorException {
        log.info("Admin: Deleting user {}", id);
        userService.deleteUser(userService.getUserById(id));
    }

    @Operation(summary = "Let somebody sign in again")
    @PostMapping("/{id}/activate")
    @PreAuthorize("hasAuthority('ADMIN')")
    public AdminUserResponse activateUser(@PathVariable Long id)
            throws ElementNotFound, LastAdministratorException {
        log.info("Admin: Activating user {}", id);
        return AdminUserResponse.fromEntity(userService.setUserActivation(id, true));
    }

    @Operation(summary = "Lock an account", description = "The account and its data stay; nobody can sign in to it.")
    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasAuthority('ADMIN')")
    public AdminUserResponse deactivateUser(@PathVariable Long id)
            throws ElementNotFound, LastAdministratorException {
        log.info("Admin: Deactivating user {}", id);
        return AdminUserResponse.fromEntity(userService.setUserActivation(id, false));
    }

    @Operation(summary = "Send somebody a password reset mail")
    @PostMapping("/{id}/password-reset")
    @PreAuthorize("hasAuthority('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void sendPasswordReset(@PathVariable Long id) throws ElementNotFound, MessagingException {
        var user = userService.getUserById(id);
        log.info("Admin: Sending a password reset mail to user {}", id);
        userService.requestPasswordReset(user.getEmailAddress());
    }
}
