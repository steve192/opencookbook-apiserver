package com.sterul.opencookbookapiserver.services;

import java.util.Collections;

import com.sterul.opencookbookapiserver.repositories.UserRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class UserDetailsServiceImpl implements UserDetailsService{

    @Autowired
    UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        var foundUser = userRepository.findByEmailAddress(username);
        if (foundUser == null ) {
            throw new UsernameNotFoundException(username);
        }
        return new User(foundUser.getEmailAddress(), foundUser.getPasswordHash(), Collections.emptyList());
    }

    public Boolean userExists(String emailAddress) {
        return userRepository.existsByEmailAddress(emailAddress);
    }

    public com.sterul.opencookbookapiserver.entities.account.User createUser(String emailAddress, String unencryptedPassword) throws UserAlreadyExistsException {
        if (userExists(emailAddress)) {
            throw new UserAlreadyExistsException("User already exists");
        }
        var createdUser = new com.sterul.opencookbookapiserver.entities.account.User();
        createdUser.setEmailAddress(emailAddress);
        createdUser.setPasswordHash(passwordEncoder.encode(unencryptedPassword));
        return userRepository.save(createdUser);
    }
    
}
