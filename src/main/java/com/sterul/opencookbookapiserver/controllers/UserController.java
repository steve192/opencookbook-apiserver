package com.sterul.opencookbookapiserver.controllers;


import com.sterul.opencookbookapiserver.entities.account.User;
import com.sterul.opencookbookapiserver.repositories.UserRepository;
import com.sterul.opencookbookapiserver.services.UserAlreadyExistsException;
import com.sterul.opencookbookapiserver.services.UserDetailsServiceImpl;
import com.sterul.opencookbookapiserver.util.JwtTokenUtil;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
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

    @Autowired
	private AuthenticationManager authenticationManager;

	@Autowired
	private JwtTokenUtil jwtTokenUtil;

	@Autowired
	private UserDetailsServiceImpl userDetailsService;

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
    public User signup(@RequestBody UserCreationRequest userCreationRequest) throws Exception {
        var createdUser = new User();

        //TODO: Move logic to user details service
        if (userDetailsService.userExists(userCreationRequest.getEmailAddress())) {
            throw new Exception("User already exists");
        }
        createdUser.setEmailAddress(userCreationRequest.emailAddress);
        createdUser.setPasswordHash(passwordEncoder.encode(userCreationRequest.password));

        userRepository.save(createdUser);

        return createdUser;
    }

    static class UserLoginRequest {
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

    public class UserLoginResponse{

        private final String token;
    
        public UserLoginResponse(String token) {
            this.token = token;
        }
    
        public String getToken() {
            return this.token;
        }
    }

    @PostMapping("/login")
    public ResponseEntity<UserLoginResponse> login(@RequestBody UserLoginRequest authenticationRequest) throws Exception {
        login(authenticationRequest.getEmailAddress(), authenticationRequest.getPassword());

		final UserDetails userDetails = userDetailsService
				.loadUserByUsername(authenticationRequest.getEmailAddress());

		final String token = jwtTokenUtil.generateToken(userDetails);

		return ResponseEntity.ok(new UserLoginResponse(token));
    }

    private void login(String username, String password) throws Exception {
		try {
			authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(username, password));
		} catch (DisabledException e) {
			throw new Exception("USER_DISABLED", e);
		} catch (BadCredentialsException e) {
			throw new Exception("INVALID_CREDENTIALS", e);
		}
	}

    
    
}
