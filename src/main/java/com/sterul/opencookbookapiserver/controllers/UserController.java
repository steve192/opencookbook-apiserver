package com.sterul.opencookbookapiserver.controllers;

import com.sterul.opencookbookapiserver.controllers.exceptions.NotAuthorizedException;
import com.sterul.opencookbookapiserver.controllers.exceptions.UnauthorizedException;
import com.sterul.opencookbookapiserver.controllers.requests.RefreshTokenRequest;
import com.sterul.opencookbookapiserver.controllers.requests.UserCreationRequest;
import com.sterul.opencookbookapiserver.controllers.requests.UserLoginRequest;
import com.sterul.opencookbookapiserver.controllers.responses.RefreshTokenResponse;
import com.sterul.opencookbookapiserver.controllers.responses.UserLoginResponse;
import com.sterul.opencookbookapiserver.entities.RefreshToken;
import com.sterul.opencookbookapiserver.entities.account.User;
import com.sterul.opencookbookapiserver.services.RefreshTokenService;
import com.sterul.opencookbookapiserver.services.UserDetailsServiceImpl;
import com.sterul.opencookbookapiserver.services.UserService;
import com.sterul.opencookbookapiserver.services.exceptions.ElementNotFound;
import com.sterul.opencookbookapiserver.util.JwtTokenUtil;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController extends BaseController {

	@Autowired
	private AuthenticationManager authenticationManager;

	@Autowired
	private JwtTokenUtil jwtTokenUtil;

	@Autowired
	private UserDetailsServiceImpl userDetailsService;

	@Autowired
	private UserService userService;

	@Autowired
	private RefreshTokenService refreshTokenService;

	@PostMapping("/signup")
	public User signup(@RequestBody UserCreationRequest userCreationRequest) throws Exception {
		return userService.createUser(userCreationRequest.getEmailAddress(), userCreationRequest.getPassword());
	}

	@PostMapping("/login")
	public ResponseEntity<UserLoginResponse> login(@RequestBody UserLoginRequest authenticationRequest)
			throws UnauthorizedException {
		login(authenticationRequest.getEmailAddress(), authenticationRequest.getPassword());

		final UserDetails userDetails = userDetailsService
				.loadUserByUsername(authenticationRequest.getEmailAddress());

		final String token = jwtTokenUtil.generateToken(userDetails);

		var response = new UserLoginResponse();
		response.setToken(token);
		response.setRefreshToken(refreshTokenService
				.createRefreshTokenForUser(userService.getUserByEmail(userDetails.getUsername())).getToken());

		return ResponseEntity.ok(response);
	}

	@PostMapping("/refreshToken")
	public RefreshTokenResponse renewToken(@RequestBody RefreshTokenRequest refreshTokenRequest)
			throws NotAuthorizedException {
		if (!refreshTokenService.isTokenValid(refreshTokenRequest.getRefreshToken())) {
			throw new NotAuthorizedException();
		}
		RefreshToken refreshToken;
		try {
			refreshToken = refreshTokenService.getRefreshToken(refreshTokenRequest.getRefreshToken());
		} catch (ElementNotFound e) {
			throw new NotAuthorizedException();
		}
		var userDetails = userDetailsService.loadUserByUsername(refreshToken.getOwner().getEmailAddress());
		var jwtToken = jwtTokenUtil.generateToken(userDetails);

		var response = new RefreshTokenResponse();
		response.setToken(jwtToken);
		return response;
	}

	private void login(String username, String password) throws UnauthorizedException {
		try {
			authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(username, password));
		} catch (DisabledException | BadCredentialsException e) {
			throw new UnauthorizedException();
		}
	}

}
