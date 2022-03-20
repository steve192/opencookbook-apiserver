package com.sterul.opencookbookapiserver.services;

import com.sterul.opencookbookapiserver.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    @Autowired
    UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        var foundUser = userRepository.findByEmailAddress(username);
        if (foundUser == null) {
            throw new UsernameNotFoundException(username);
        }

        return new User(foundUser.getEmailAddress(),
                foundUser.getPasswordHash(),
                foundUser.isActivated(),
                true,
                true,
                true,
                Collections.emptyList());
    }

}
