package com.sterul.opencookbookapiserver.configurations.security;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.sterul.opencookbookapiserver.repositories.UserRepository;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    public UserDetailsServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

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
                AccountAuthorities.of(foundUser));
    }

}
