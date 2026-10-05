package com.sterul.opencookbookapiserver.controllers;

import java.util.stream.Stream;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.sterul.opencookbookapiserver.configurations.security.CurrentSignIn;
import com.sterul.opencookbookapiserver.configurations.security.NotForDemoAccounts;
import com.sterul.opencookbookapiserver.controllers.exceptions.UnauthorizedException;
import com.sterul.opencookbookapiserver.controllers.requests.DisplayNameRequest;
import com.sterul.opencookbookapiserver.controllers.requests.LogoutRequest;
import com.sterul.opencookbookapiserver.controllers.requests.PasswordChangeRequest;
import com.sterul.opencookbookapiserver.controllers.requests.PasswordResetExecutionRequest;
import com.sterul.opencookbookapiserver.controllers.requests.PasswordResetRequest;
import com.sterul.opencookbookapiserver.controllers.requests.RefreshTokenRequest;
import com.sterul.opencookbookapiserver.controllers.requests.ResendActivationLinkRequest;
import com.sterul.opencookbookapiserver.controllers.requests.ShoppingProviderRequest;
import com.sterul.opencookbookapiserver.controllers.requests.UserCreationRequest;
import com.sterul.opencookbookapiserver.controllers.requests.UserLoginRequest;
import com.sterul.opencookbookapiserver.controllers.responses.RefreshTokenResponse;
import com.sterul.opencookbookapiserver.controllers.responses.SignupResponse;
import com.sterul.opencookbookapiserver.controllers.responses.UserInfoResponse;
import com.sterul.opencookbookapiserver.controllers.responses.UserLoginResponse;
import com.sterul.opencookbookapiserver.entities.account.Role;
import com.sterul.opencookbookapiserver.errors.ApiException;
import com.sterul.opencookbookapiserver.services.SignInService;
import com.sterul.opencookbookapiserver.services.SignedInUserService;
import com.sterul.opencookbookapiserver.services.SignupService;
import com.sterul.opencookbookapiserver.services.UserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.mail.MessagingException;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users", description = "Authentication and management of own user")
public class UserController extends BaseController {

    private final AuthenticationManager authenticationManager;
    private final SignInService signIns;
    private final UserService userService;
    private final SignupService signupService;

    public UserController(AuthenticationManager authenticationManager, SignInService signIns,
            UserService userService, SignupService signupService,
            SignedInUserService signedInUser) {
        super(signedInUser);
        this.authenticationManager = authenticationManager;
        this.signIns = signIns;
        this.userService = userService;
        this.signupService = signupService;
    }

    @Operation(summary = "Creates a new user",
            description = "With an invitation the account is active at once. Without one it waits for "
                    + "the mailed confirmation link, or for an administrator on an instance without mail.")
    @PostMapping("/signup")
    public SignupResponse signup(@Valid @RequestBody UserCreationRequest request) {
        return new SignupResponse(signupService.signup(request.emailAddress(), request.password(),
                request.invitation()));
    }

    @Operation(summary = "Logs a user in", description = "Logs in and generates tokens for authentication")
    @PostMapping("/login")
    public ResponseEntity<UserLoginResponse> login(@Valid @RequestBody UserLoginRequest authenticationRequest) {
        authenticate(authenticationRequest.emailAddress(), authenticationRequest.password());

        var user = userService.getUserByEmail(authenticationRequest.emailAddress());
        // Signing in is the clearest statement a client makes about which language it is in.
        userService.rememberLanguageOfCurrentRequest(user);

        return ResponseEntity.ok(UserLoginResponse.of(signIns.signInWithPassword(user)));
    }

    @Operation(summary = "Request a password reset for the given user",
            description = "Answers 200 whether or not the address belongs to an account, so that "
                    + "this cannot be used to find out which addresses are registered. The "
                    + "exceptions are an instance without mail and a mail server that will not "
                    + "accept the message, which are reported rather than silently swallowed.")
    @PostMapping("/requestPasswordReset")
    public ResponseEntity<Void> requestPasswordReset(
            @Valid @RequestBody PasswordResetRequest passwordResetRequest)
            throws MessagingException {
        userService.requestPasswordReset(passwordResetRequest.getEmailAddress());
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Executes a password reset")
    @PostMapping("/resetPassword")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody PasswordResetExecutionRequest request) {
        userService.resetPassword(request.getNewPassword(), request.getPasswordResetId());
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Sets a new password", description = "Every other sign in of the account ends.")
    @NotForDemoAccounts
    @PostMapping("/changePassword")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody PasswordChangeRequest request,
            @CurrentSignIn String sessionId) {
        var loggedInUser = this.getLoggedInUser();
        if (!userService.isPasswordCorrect(loggedInUser.getEmailAddress(), request.getOldPassword())) {
            throw new UnauthorizedException();
        }

        userService.changePassword(loggedInUser, request.getNewPassword(), sessionId);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Activates a user, using an activation id")
    @GetMapping("/activate")
    public ResponseEntity<UserLoginResponse> activateUser(@Valid @RequestParam String activationId) {
        var user = userService.activateUser(activationId);
        return ResponseEntity.ok(UserLoginResponse.of(signIns.signInWithoutPassword(user)));
    }

    @Operation(summary = "Resends an activation link to the users email address. Ignores requests for accounts that do not wait for the confirmation, addresses without an account and instances without mail")
    @PostMapping("/resendActivationLink")
    public ResponseEntity<Void> resendActivationLink(
            @Valid @RequestBody ResendActivationLinkRequest request) throws MessagingException {
        userService.resendActivationLink(request.getEmailAddress());
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Get information about authenticated user account")
    @GetMapping("/self")
    public UserInfoResponse getOwnUserInfo() {
        var user = getLoggedInUser();
        var response = new UserInfoResponse();
        response.setEmail(user.getEmailAddress());
        response.setDisplayName(user.getDisplayName());
        response.setOnboarded(user.isOnboarded());
        response.setShoppingProvider(user.getShoppingProvider());
        // The account's role, not the request's authorities, which also say how it signed in.
        response.setRoles(Stream.ofNullable(user.getRoles()).map(Role::name).toList());
        return response;
    }

    @Operation(summary = "Set the name fellow household members see",
            description = "A blank name clears it; the account then falls back to a masked address.")
    @PutMapping("/self/displayName")
    public UserInfoResponse setDisplayName(@Valid @RequestBody DisplayNameRequest request) {
        userService.setDisplayName(getLoggedInUser(), request.displayName());
        return getOwnUserInfo();
    }

    @Operation(summary = "Choose where shopping imports go",
            description = "The app asks on the first import and shows only the chosen provider afterwards.")
    @PutMapping("/self/shoppingProvider")
    public UserInfoResponse setShoppingProvider(@Valid @RequestBody ShoppingProviderRequest request) {
        userService.setShoppingProvider(getLoggedInUser(), request.provider());
        return getOwnUserInfo();
    }

    @Operation(summary = "Finish the first-run setup",
            description = "Takes the display name the setup screen asked for and marks the account "
                    + "as set up, so it is only ever asked once.")
    @PostMapping("/self/onboarding")
    public UserInfoResponse completeOnboarding(@Valid @RequestBody DisplayNameRequest request) {
        userService.completeOnboarding(getLoggedInUser(), request.displayName());
        return getOwnUserInfo();
    }

    @Operation(summary = "Delete authenticated user account",
            description = "The last administrator who can sign in cannot delete themselves; the "
                    + "instance would be left with nobody able to administer it.")
    @NotForDemoAccounts
    @DeleteMapping("/self")
    public ResponseEntity<Void> deleteOwnUser() {
        userService.deleteUser(getLoggedInUser());
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Generate a JWT token from a refresh token",
            description = "The JWT token is used to authenticate against all apis using the \"Authorization: Bearer "
                    + "<token>\" header field. The answer also carries the refresh token to use from now on; "
                    + "presenting a replaced one again ends the sign in.")
    @PostMapping("/refreshToken")
    public RefreshTokenResponse renewToken(@Valid @RequestBody RefreshTokenRequest refreshTokenRequest) {
        var tokens = signIns.rotate(refreshTokenRequest.getRefreshToken());
        // The app renews its token every few minutes, which makes this the place where a change
        // of app language is noticed without waiting for the next sign in. It only writes when
        // the answer is different from the stored one.
        userService.rememberLanguageOfCurrentRequest(tokens.user());

        var response = new RefreshTokenResponse();
        response.setToken(tokens.accessToken());
        response.setRefreshToken(tokens.refreshToken());
        return response;
    }

    @Operation(summary = "Sign out", description = "Ends the sign in the refresh token belongs to, on this device. "
            + "Answers the same for a token that is unknown or already spent.")
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody LogoutRequest request) {
        signIns.end(request.refreshToken());
    }

    private void authenticate(String username, String password) {
        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(username, password));
        } catch (BadCredentialsException e) {
            throw new UnauthorizedException();
        } catch (DisabledException e) {
            throw new ApiException(userService.explainLockedAccount(username));
        }
    }
}
