package com.sterul.opencookbookapiserver.controllers.admin;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.services.UserService;
import com.sterul.opencookbookapiserver.services.exceptions.ElementNotFound;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;


@RestController
@RequestMapping("/api/v1/admin/users")
@Tag(name = "Users", description = "Admin user api")
@Slf4j
public class AdminUserController {

    private UserService userService;

    public AdminUserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public List<CookpalUser> getAll() {
        log.info("Admin: Accessing all users");
        return userService.getAllUsers();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public void deleteUser(@PathVariable Long id) throws ElementNotFound {
        var user = userService.getUserById(id);
        userService.deleteUser(user);
    }

    @PostMapping("/{id]/activate")
    @PreAuthorize("hasAuthority('ADMIN')")
    public void activateUser(@PathVariable Long id) throws ElementNotFound {
        userService.activateUserById(id);
    }

}
