package com.sterul.opencookbookapiserver.controllers;


import com.sterul.opencookbookapiserver.entities.account.User;
import com.sterul.opencookbookapiserver.repositories.UserRepository;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/api/v1/users")
public class UserController {
    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;

    public UserController(UserRepository userRepository, PasswordEncoder bCryptPasswordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = bCryptPasswordEncoder;
    }
    
    static class UserCreationRequest {
        private String emailAddress;
        private String password;

        public String getEmailAddress() {
            return emailAddress;
        }
        public void setEmailAddress(String emailAddress) {
            this.emailAddress = emailAddress;
        }
        public String getPassword() {
            return password;
        }
        public void setPassword(String password) {
            this.password = password;
        }
    }

    @PostMapping("/signup")
    public User signup(@RequestBody UserCreationRequest userCreationRequest) {
        var createdUser = new User();

        //TODO: Check if user already exists
        createdUser.setEmailAddress(userCreationRequest.emailAddress);
        createdUser.setPasswordHash(passwordEncoder.encode(userCreationRequest.password));

        userRepository.save(createdUser);

        return createdUser;
    }

    @PostMapping("/login")
    public User login(@RequestBody User user) {
        
    }

    
    
}
