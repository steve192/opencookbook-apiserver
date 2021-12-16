package com.sterul.opencookbookapiserver.controllers;

import com.sterul.opencookbookapiserver.controllers.exceptions.UnauthorizedException;
import com.sterul.opencookbookapiserver.controllers.requests.UserCreationRequest;
import com.sterul.opencookbookapiserver.controllers.requests.UserLoginRequest;
import com.sterul.opencookbookapiserver.controllers.responses.UserLoginResponse;
import com.sterul.opencookbookapiserver.entities.account.User;
import com.sterul.opencookbookapiserver.services.UserDetailsServiceImpl;
import com.sterul.opencookbookapiserver.util.JwtTokenUtil;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

	@Autowired
	private AuthenticationManager authenticationManager;

	@Autowired
	private JwtTokenUtil jwtTokenUtil;

	@Autowired
	private UserDetailsServiceImpl userDetailsService;

	@PostMapping("/signup")
	public User signup(@RequestBody UserCreationRequest userCreationRequest) throws Exception {
		return userDetailsService.createUser(userCreationRequest.getEmailAddress(), userCreationRequest.getPassword());
	}

	@PostMapping("/login")
	public ResponseEntity<UserLoginResponse> login(@RequestBody UserLoginRequest authenticationRequest)
			throws Exception {
		login(authenticationRequest.getEmailAddress(), authenticationRequest.getPassword());

		final UserDetails userDetails = userDetailsService
				.loadUserByUsername(authenticationRequest.getEmailAddress());

		final String token = jwtTokenUtil.generateToken(userDetails);

		return ResponseEntity.ok(new UserLoginResponse(token));
	}

	@GetMapping("/renewToken")
	public void renewToken() {
		// TODO: Implement
	}

	private void login(String username, String password) throws Exception {
		try {
			authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(username, password));
		} catch (DisabledException | BadCredentialsException e) {
			throw new UnauthorizedException();
		}
	}

}
