package com.sterul.opencookbookapiserver.unit.services.google;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.UnaryOperator;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.sterul.opencookbookapiserver.configurations.OpencookbookConfiguration;
import com.sterul.opencookbookapiserver.errors.ApiErrorCode;
import com.sterul.opencookbookapiserver.errors.ApiException;
import com.sterul.opencookbookapiserver.services.google.GoogleIdTokens;
import com.sterul.opencookbookapiserver.services.google.GoogleIdTokens.VerifiedAddress;

class GoogleIdTokensTest {

    private static final String WEB_CLIENT = "web.apps.googleusercontent.com";
    private static final String ANDROID_CLIENT = "android.apps.googleusercontent.com";
    private static final String ADDRESS = "anna@gmail.com";

    private static final KeyPair GOOGLES_KEYS = rsaKeys();

    private final GoogleIdTokens cut = new GoogleIdTokens(
            NimbusJwtDecoder.withPublicKey((RSAPublicKey) GOOGLES_KEYS.getPublic()).build(), clients());

    @Test
    void aTokenForTheWebAppNamesTheVerifiedAddress() {
        assertEquals(new VerifiedAddress(ADDRESS, true), cut.verifiedAddress(token(claims -> claims)));
    }

    @Test
    void aWorkspaceAddressIsHostedByGoogle() {
        var workspace = token(claims -> claims.claim("email", "anna@example.com").claim("hd", "example.com"));
        assertEquals(new VerifiedAddress("anna@example.com", true), cut.verifiedAddress(workspace));
    }

    @Test
    void anyOtherAddressIsNotHostedByGoogle() {
        var other = token(claims -> claims.claim("email", "anna@gmx.de"));
        assertEquals(new VerifiedAddress("anna@gmx.de", false), cut.verifiedAddress(other));
    }

    @Test
    void aTokenForTheAndroidAppIsAcceptedToo() {
        assertEquals(ADDRESS, cut.verifiedAddress(token(claims -> claims.audience(List.of(ANDROID_CLIENT)))).emailAddress());
    }

    @Test
    void bothSpellingsOfGooglesIssuerAreAccepted() {
        assertEquals(ADDRESS, cut.verifiedAddress(token(claims -> claims.issuer("accounts.google.com"))).emailAddress());
    }

    @Test
    void aTokenForAnotherAppIsRefused() {
        assertRefused(token(claims -> claims.audience(List.of("someone-else.apps.googleusercontent.com"))));
    }

    @Test
    void aTokenFromAnotherIssuerIsRefused() {
        assertRefused(token(claims -> claims.issuer("https://accounts.example.com")));
    }

    @Test
    void anUnverifiedAddressIsRefused() {
        assertRefused(token(claims -> claims.claim("email_verified", false)));
    }

    @Test
    void aTokenWithoutAnAddressIsRefused() {
        assertRefused(token(claims -> claims.claims(all -> all.remove("email"))));
    }

    @Test
    void anExpiredTokenIsRefused() {
        var anHourAgo = Instant.now().minus(Duration.ofHours(1));
        assertRefused(token(claims -> claims.issuedAt(anHourAgo.minus(Duration.ofHours(1))).expiresAt(anHourAgo)));
    }

    @Test
    void aTokenSignedByAnybodyButGoogleIsRefused() {
        assertRefused(token(rsaKeys(), claims -> claims));
    }

    @Test
    void somethingThatIsNotATokenIsRefused() {
        assertRefused("not-a-token");
    }

    private void assertRefused(String idToken) {
        var refusal = assertThrows(ApiException.class, () -> cut.verifiedAddress(idToken));
        assertEquals(ApiErrorCode.GOOGLE_SIGN_IN_FAILED, refusal.getErrorCode());
    }

    private static String token(UnaryOperator<JwtClaimsSet.Builder> changes) {
        return token(GOOGLES_KEYS, changes);
    }

    private static String token(KeyPair keys, UnaryOperator<JwtClaimsSet.Builder> changes) {
        var now = Instant.now();
        var claims = JwtClaimsSet.builder()
                .issuer("https://accounts.google.com")
                .audience(List.of(WEB_CLIENT))
                .subject("110169484474386276334")
                .issuedAt(now)
                .expiresAt(now.plus(Duration.ofHours(1)))
                .claim("email", ADDRESS)
                .claim("email_verified", true);
        var encoder = NimbusJwtEncoder
                .withKeyPair((RSAPublicKey) keys.getPublic(), (RSAPrivateKey) keys.getPrivate())
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(),
                changes.apply(claims).build())).getTokenValue();
    }

    private static OpencookbookConfiguration.Auth.Google clients() {
        var google = new OpencookbookConfiguration.Auth.Google();
        google.setClientId(WEB_CLIENT);
        google.setAndroidClientId(ANDROID_CLIENT);
        return google;
    }

    private static KeyPair rsaKeys() {
        try {
            var generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
