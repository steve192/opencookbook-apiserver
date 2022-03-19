package com.sterul.opencookbookapiserver.controllers;

import com.sterul.opencookbookapiserver.controllers.exceptions.NotAuthorizedException;
import com.sterul.opencookbookapiserver.controllers.exceptions.UnauthorizedException;
import com.sterul.opencookbookapiserver.controllers.requests.RefreshTokenRequest;
import com.sterul.opencookbookapiserver.controllers.requests.UserCreationRequest;
import com.sterul.opencookbookapiserver.controllers.requests.UserLoginRequest;
import com.sterul.opencookbookapiserver.controllers.responses.RefreshTokenResponse;
import com.sterul.opencookbookapiserver.controllers.responses.UserInfoResponse;
import com.sterul.opencookbookapiserver.controllers.responses.UserLoginResponse;
import com.sterul.opencookbookapiserver.entities.RefreshToken;
import com.sterul.opencookbookapiserver.entities.account.User;
import com.sterul.opencookbookapiserver.services.EmailService;
import com.sterul.opencookbookapiserver.services.RefreshTokenService;
import com.sterul.opencookbookapiserver.services.UserDetailsServiceImpl;
import com.sterul.opencookbookapiserver.services.UserService;
import com.sterul.opencookbookapiserver.services.exceptions.ElementNotFound;
import com.sterul.opencookbookapiserver.services.exceptions.InvalidActivationLinkException;
import com.sterul.opencookbookapiserver.services.exceptions.UserAlreadyExistsException;
import com.sterul.opencookbookapiserver.util.JwtTokenUtil;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import javax.mail.MessagingException;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users", description = "Authentication and management of own user")
@Slf4j
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

	@Autowired
	private EmailService emailService;

	@Operation(summary = "Creates a new user")
	@PostMapping("/signup")
	public User signup(@RequestBody UserCreationRequest userCreationRequest) throws UserAlreadyExistsException {
		var createdUser = userService.createUser(userCreationRequest.getEmailAddress(), userCreationRequest.getPassword());
		var activationLink = userService.createActivationLink(createdUser);
		try {
			emailService.sendActivationMail(activationLink);
		} catch (MessagingException e) {
			log.error("Error sending activation mail");
		}
		return createdUser;
	}

	@Operation(summary = "Logs a user in", description = "Logs in and generates tokens for authentication")
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

	@Operation( summary = "Activates a user, using an activation id")
	@GetMapping("/activate")
	public void activateUser(@RequestParam String activationId) throws InvalidActivationLinkException {
		userService.activateUser(activationId);
	}

	@Operation(summary = "Get information about authenticated user account")
	@GetMapping("/self")
	public UserInfoResponse getOwnUserInfo() {
		var response = new UserInfoResponse();
		response.setEmail(getLoggedInUser().getEmailAddress());
		return response;
	}

	@Operation(summary = "Delete authenticated user account")
	@DeleteMapping("/self")
	public void deleteOwnUser() {
		userService.deleteUser(getLoggedInUser());
	}

	@Operation(summary = "Generate a JWT token from a refresh token", description = "The JWT token is used to authenticate against all apis using the \"Authentication: Bearer < token >\" header field")
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
