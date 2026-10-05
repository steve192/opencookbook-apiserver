package com.sterul.opencookbookapiserver.services.google;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.oidc.StandardClaimNames;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.sterul.opencookbookapiserver.configurations.OpencookbookConfiguration;
import com.sterul.opencookbookapiserver.configurations.google.ConditionalOnGoogleSignIn;
import com.sterul.opencookbookapiserver.errors.ApiErrorCode;
import com.sterul.opencookbookapiserver.errors.ApiException;

/**
 * Checks ID tokens Google issued to this instance's OAuth clients, against Google's published keys.
 * Deliberately not a {@link JwtDecoder} bean: that one checks this server's own access tokens.
 */
@Service
@ConditionalOnGoogleSignIn
public class GoogleIdTokens {

    private static final String JWK_SET_URI = "https://www.googleapis.com/oauth2/v3/certs";
    private static final Set<String> ISSUERS = Set.of("https://accounts.google.com", "accounts.google.com");
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final List<String> GMAIL_DOMAINS = List.of("@gmail.com", "@googlemail.com");
    /** Set for Google Workspace accounts. */
    private static final String HOSTED_DOMAIN = "hd";

    private final JwtDecoder decoder;

    /**
     * @param hostedByGoogle Gmail or Workspace, whose owner Google still vouches for. Any other address was
     *                       only checked when the Google account was made and may have changed hands since.
     */
    public record VerifiedAddress(String emailAddress, boolean hostedByGoogle) {
    }

    @Autowired
    public GoogleIdTokens(OpencookbookConfiguration configuration) {
        this(NimbusJwtDecoder.withJwkSetUri(JWK_SET_URI).restOperations(restTemplate()).build(),
                configuration.getAuth().getGoogle());
    }

    public GoogleIdTokens(NimbusJwtDecoder decoder, OpencookbookConfiguration.Auth.Google google) {
        decoder.setJwtValidator(validator(google.audiences()));
        this.decoder = decoder;
    }

    /** The address Google verified for whoever holds the token. */
    public VerifiedAddress verifiedAddress(String idToken) {
        try {
            var token = decoder.decode(idToken);
            var emailAddress = token.getClaimAsString(StandardClaimNames.EMAIL);
            var gmail = GMAIL_DOMAINS.stream().anyMatch(emailAddress.toLowerCase(Locale.ROOT)::endsWith);
            return new VerifiedAddress(emailAddress, gmail || token.hasClaim(HOSTED_DOMAIN));
        } catch (BadJwtException e) {
            throw new ApiException(ApiErrorCode.GOOGLE_SIGN_IN_FAILED, e.getMessage(), e);
        } catch (JwtException e) {
            throw new ApiException(ApiErrorCode.TEMPORARILY_UNAVAILABLE, "Google's signing keys are unavailable", e);
        }
    }

    private static OAuth2TokenValidator<Jwt> validator(List<String> audiences) {
        return JwtValidators.createDefaultWithValidators(List.<OAuth2TokenValidator<Jwt>>of(
                new JwtClaimValidator<String>(JwtClaimNames.ISS, issuer -> issuer != null && ISSUERS.contains(issuer)),
                new JwtClaimValidator<List<String>>(JwtClaimNames.AUD,
                        audience -> audience != null && audience.stream().anyMatch(audiences::contains)),
                new JwtClaimValidator<Boolean>(StandardClaimNames.EMAIL_VERIFIED, Boolean.TRUE::equals),
                new JwtClaimValidator<String>(StandardClaimNames.EMAIL, email -> email != null && !email.isBlank())));
    }

    private static RestTemplate restTemplate() {
        var requests = new SimpleClientHttpRequestFactory();
        requests.setConnectTimeout(TIMEOUT);
        requests.setReadTimeout(TIMEOUT);
        return new RestTemplate(requests);
    }
}
