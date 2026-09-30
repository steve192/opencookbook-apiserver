package com.sterul.opencookbookapiserver.configurations.security;

import java.security.NoSuchAlgorithmException;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * Access tokens are signed with a key made at every start: there is nothing to configure or to leak, and
 * a restart only makes the apps renew their tokens. Several instances behind one address would need a
 * shared key instead.
 */
@Configuration
public class AccessTokenConfiguration {

    @Bean
    SecretKey accessTokenSigningKey() throws NoSuchAlgorithmException {
        return KeyGenerator.getInstance("HmacSHA256").generateKey();
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey accessTokenSigningKey) {
        return NimbusJwtEncoder.withSecretKey(accessTokenSigningKey).algorithm(MacAlgorithm.HS256).build();
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey accessTokenSigningKey) {
        return NimbusJwtDecoder.withSecretKey(accessTokenSigningKey).macAlgorithm(MacAlgorithm.HS256).build();
    }
}
