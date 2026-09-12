package com.sterul.opencookbookapiserver.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sterul.opencookbookapiserver.controllers.exceptions.NotAuthorizedException;
import com.sterul.opencookbookapiserver.controllers.exceptions.UnauthorizedException;
import com.sterul.opencookbookapiserver.controllers.exceptions.UserNotActiveException;
import com.sterul.opencookbookapiserver.controllers.requests.PasswordChangeRequest;
import com.sterul.opencookbookapiserver.controllers.requests.PasswordResetExecutionRequest;
import com.sterul.opencookbookapiserver.controllers.requests.PasswordResetRequest;
import com.sterul.opencookbookapiserver.controllers.requests.RefreshTokenRequest;
import com.sterul.opencookbookapiserver.controllers.requests.ResendActivationLinkRequest;
import com.sterul.opencookbookapiserver.controllers.requests.UserCreationRequest;
import com.sterul.opencookbookapiserver.controllers.requests.UserLoginRequest;
import com.sterul.opencookbookapiserver.controllers.responses.RefreshTokenResponse;
import com.sterul.opencookbookapiserver.controllers.responses.UserInfoResponse;
import com.sterul.opencookbookapiserver.controllers.responses.UserLoginResponse;
import com.sterul.opencookbookapiserver.entities.RefreshToken;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.entities.account.Role;
import com.sterul.opencookbookapiserver.errors.ApiErrorCode;
import com.sterul.opencookbookapiserver.errors.ApiException;
import com.sterul.opencookbookapiserver.services.AuthRateLimiter;
import com.sterul.opencookbookapiserver.services.EmailService;
import com.sterul.opencookbookapiserver.services.RefreshTokenService;
import com.sterul.opencookbookapiserver.services.UserDetailsServiceImpl;
import com.sterul.opencookbookapiserver.services.UserService;
import com.sterul.opencookbookapiserver.services.exceptions.ElementNotFound;
import com.sterul.opencookbookapiserver.services.exceptions.LastAdministratorException;
import com.sterul.opencookbookapiserver.services.exceptions.InvalidActivationLinkException;
import com.sterul.opencookbookapiserver.services.exceptions.PasswordResetLinkNotExistingException;
import com.sterul.opencookbookapiserver.services.exceptions.SignupDisabledException;
import com.sterul.opencookbookapiserver.services.exceptions.UserAlreadyExistsException;
import com.sterul.opencookbookapiserver.services.mail.MailLanguages;
import com.sterul.opencookbookapiserver.util.JwtTokenUtil;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.mail.MessagingException;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;

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

    @Autowired
    private MailLanguages mailLanguages;

    @Autowired
    private AuthRateLimiter authRateLimiter;

    @Operation(summary = "Creates a new user")
    @PostMapping("/signup")
    @Transactional
    public CookpalUser signup(@Valid @RequestBody UserCreationRequest userCreationRequest)
            throws UserAlreadyExistsException, SignupDisabledException {
        // Whatever the client asked for in Accept-Language, which is the only thing known about
        // a person who does not have an account yet.
        var createdUser = userService.createUser(userCreationRequest.emailAddress(),
                userCreationRequest.password(), mailLanguages.fromCurrentRequest().orElse(null));
        var activationLink = userService.createActivationLink(createdUser);
        try {
            emailService.sendActivationMail(activationLink);
        } catch (MessagingException e) {
            // The account exists either way, and the link can be sent again from the login
            // screen, so a mail server that is down must not undo a signup.
            log.error("Error sending activation mail", e);
        }
        return createdUser;
    }

    @Operation(summary = "Logs a user in", description = "Logs in and generates tokens for authentication")
    @PostMapping("/login")
    public ResponseEntity<UserLoginResponse> login(@Valid @RequestBody UserLoginRequest authenticationRequest)
            throws UnauthorizedException, UserNotActiveException {

        try {
            login(authenticationRequest.emailAddress(), authenticationRequest.password());
        } catch (UserNotActiveException e) {
            // Somebody trying to sign in has lost or never received the link, so send it again
            // before saying why they cannot. A mail server that is down must not turn that into
            // a different answer than the one they need to read.
            try {
                resendActivationLinkWithinBudget(authenticationRequest.emailAddress());
            } catch (MessagingException e1) {
                log.error("Error re-sending activation link for user", e1);
            }
            throw e;
        }

        final UserDetails userDetails = userDetailsService
                .loadUserByUsername(authenticationRequest.emailAddress());

        final String token = jwtTokenUtil.generateToken(userDetails);

        var user = userService.getUserByEmail(userDetails.getUsername());
        // Signing in is the clearest statement a client makes about which language it is in.
        userService.rememberLanguageOfCurrentRequest(user);

        var response = UserLoginResponse.builder()
                .token(token)
                .userActive(true)
                .refreshToken(refreshTokenService.createRefreshTokenForUser(user).getToken())
                .build();

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Request a password reset for the given user",
            description = "Answers 200 whether or not the address belongs to an account, so that "
                    + "this cannot be used to find out which addresses are registered. The one "
                    + "exception is a mail server that will not accept the message, which is "
                    + "reported rather than silently swallowed.")
    @PostMapping("/requestPasswordReset")
    public ResponseEntity<String> requestPasswordReset(
            @Valid @RequestBody PasswordResetRequest passwordResetRequest)
            throws MessagingException {
        var emailAddress = passwordResetRequest.getEmailAddress();
        if (userService.userExists(emailAddress) && mayMail(emailAddress)) {
            userService.requestPasswordReset(emailAddress);
        }
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Executes a password reset")
    @PostMapping("/resetPassword")
    public ResponseEntity<String> resetPassword(@Valid @RequestBody PasswordResetExecutionRequest request)
            throws PasswordResetLinkNotExistingException {
        userService.resetPassword(request.getNewPassword(), request.getPasswordResetId());
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Sets a new password")
    @PostMapping("/changePassword")
    public ResponseEntity<String> changePassword(@Valid @RequestBody PasswordChangeRequest request)
            throws UnauthorizedException {
        var loggedInUser = this.getLoggedInUser();
        if (!userService.isPasswordCorrect(loggedInUser.getEmailAddress(), request.getOldPassword())) {
            throw new UnauthorizedException();
        }

        userService.changePassword(loggedInUser, request.getNewPassword());
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Activates a user, using an activation id")
    @GetMapping("/activate")
    public ResponseEntity<UserLoginResponse> activateUser(@Valid @RequestParam String activationId)
            throws InvalidActivationLinkException {
        var user = userService.activateUser(activationId);

        final UserDetails userDetails = userDetailsService
                .loadUserByUsername(user.getEmailAddress());

        final String token = jwtTokenUtil.generateToken(userDetails);

        var response = UserLoginResponse.builder()
                .token(token)
                .userActive(true)
                .refreshToken(refreshTokenService.createRefreshTokenForUser(
                        user).getToken())
                .build();

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Resends an activation link to the users email address. Ignores requests for when users are already active or users do not exist")
    @PostMapping("/resendActivationLink")
    public ResponseEntity<String> resendActivationLink(
            @Valid @RequestBody ResendActivationLinkRequest request) throws MessagingException {
        resendActivationLinkWithinBudget(request.getEmailAddress());
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Get information about authenticated user account")
    @GetMapping("/self")
    public UserInfoResponse getOwnUserInfo() {
        var user = getLoggedInUser();
        var response = new UserInfoResponse();
        response.setEmail(user.getEmailAddress());
        response.setRoles(SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .map(authority -> authority.getAuthority()).toList());
        return response;
    }

    @Operation(summary = "Delete authenticated user account",
            description = "The last administrator who can sign in cannot delete themselves; the "
                    + "instance would be left with nobody able to administer it.")
    @DeleteMapping("/self")
    public ResponseEntity deleteOwnUser() throws LastAdministratorException, NotAuthorizedException {
        var user = getLoggedInUser();
        if (Role.DEMO.equals(user.getRoles())) {
            throw new NotAuthorizedException();
        }
        userService.deleteUser(user);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Generate a JWT token from a refresh token", description = "The JWT token is used to authenticate against all apis using the \"Authentication: Bearer < token >\" header field")
    @PostMapping("/refreshToken")
    public RefreshTokenResponse renewToken(@Valid @RequestBody RefreshTokenRequest refreshTokenRequest)
            throws ApiException {
        // "Sign in again", not "you may not": a spent refresh token is the app's cue to send
        // somebody back to the login screen, and 403 reads as a permission they will never have.
        if (!refreshTokenService.isTokenValid(refreshTokenRequest.getRefreshToken())) {
            throw new ApiException(ApiErrorCode.AUTHENTICATION_REQUIRED, "Refresh token expired");
        }
        RefreshToken refreshToken;
        try {
            refreshToken = refreshTokenService.getRefreshToken(refreshTokenRequest.getRefreshToken());
        } catch (ElementNotFound e) {
            throw new ApiException(ApiErrorCode.AUTHENTICATION_REQUIRED, "Refresh token unknown", e);
        }
        var userDetails = userDetailsService.loadUserByUsername(refreshToken.getOwner().getEmailAddress());
        // The app renews its token every few minutes, which makes this the place where a change
        // of app language is noticed without waiting for the next sign in. It only writes when
        // the answer is different from the stored one.
        userService.rememberLanguageOfCurrentRequest(refreshToken.getOwner());
        var jwtToken = jwtTokenUtil.generateToken(userDetails);

        var response = new RefreshTokenResponse();
        response.setToken(jwtToken);
        return response;
    }

    private void resendActivationLinkWithinBudget(String emailAddress) throws MessagingException {
        if (userService.userExists(emailAddress) && mayMail(emailAddress)) {
            userService.resendActivationLink(emailAddress);
        }
    }

    /**
     * Whether one more mail may be sent to an address. A spent budget is not reported: these
     * endpoints answer the same whether or not an account exists.
     *
     * @param emailAddress who would be written to
     * @return whether to send
     */
    private boolean mayMail(String emailAddress) {
        if (authRateLimiter.mayMail(emailAddress)) {
            return true;
        }
        log.warn("Not sending another mail to a recipient who has had their hourly allowance");
        return false;
    }

    private void login(String username, String password) throws UnauthorizedException, UserNotActiveException {
        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(username, password));
        } catch (BadCredentialsException e) {
            throw new UnauthorizedException();
        } catch (DisabledException e) {
            throw new UserNotActiveException();
        }
    }

}
