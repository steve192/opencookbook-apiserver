package com.sterul.opencookbookapiserver.services;

import java.util.Collections;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.sterul.opencookbookapiserver.repositories.UserRepository;

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

        var role = foundUser.getRoles();
        List<GrantedAuthority> authorities;
        if (role == null) {
            authorities = Collections.emptyList();
        } else {
            authorities = List.of(new SimpleGrantedAuthority(role.name()));
        }
        return new User(foundUser.getEmailAddress(),
                foundUser.getPasswordHash(),
                foundUser.isActivated(),
                true,
                true,
                true,
                authorities);
    }

}
