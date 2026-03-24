package com.sterul.opencookbookapiserver.configurations.security;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;

import com.sterul.opencookbookapiserver.configurations.security.requestfilters.JwtRequestFilter;
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class WebSecurityConfiguration {

        private static final String[] AUTH_WHITELIST = {
                        "/api/v1/users/signup",
                        "/api/v1/users/activate",
                        "/api/v1/users/resendActivationLink",
                        "/api/v1/users/requestPasswordReset",
                        "/api/v1/users/resetPassword",
                        "/api/v1/users/login",
                        "/api/v1/users/refreshToken",
                        "/swagger-ui/**",
                        "/v3/api-docs/**",
                        "/api-docs",
                        "/api-docs/**",
                        "/api/v1/instance",
                        "/api/v1/instance/**",
                        "/api/v1/bringexport",
                        "/api/v1/bringexport/**",
                        "/h2-console/**",
                        "/error",
                        "/actuator/health",
                        "/admin",
                        "/admin/**"
        };
        @Autowired
        private UnauthorizedEntryPoint unauthorizedEntryPoint;
        @Autowired
        private JwtRequestFilter jwtRequestFilter;

        @Bean
        public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

                // Cors and csrf not needed in an api server
                http.cors(configurer -> configurer.configurationSource(c -> allowAllCorsConfig()));
                http.csrf(conf -> conf.disable());

                // Allow frames needed for h2 console
                http.headers(config -> config.frameOptions(options -> options.sameOrigin()));

                // Permit whitelist and authenticated request
                http.authorizeHttpRequests(
                                authorize -> authorize.requestMatchers(AUTH_WHITELIST).permitAll()
                                                .anyRequest().authenticated());

                http.exceptionHandling(configurer -> configurer
                                .authenticationEntryPoint(unauthorizedEntryPoint));

                // Disable sessions since auth/session token is passed in every request
                http.sessionManagement(configurer -> configurer.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

                // Add a filter to check the sent token and authenticate
                http.addFilterBefore(jwtRequestFilter, UsernamePasswordAuthenticationFilter.class);

                return http.build();
        }

        private CorsConfiguration allowAllCorsConfig() {
                List<String> permittedCorsMethods = Collections.unmodifiableList(Arrays.asList(
                                HttpMethod.GET.name(),
                                HttpMethod.HEAD.name(),
                                HttpMethod.POST.name(),
                                HttpMethod.PUT.name(),
                                HttpMethod.DELETE.name()));

                var corsConfiguration = new CorsConfiguration().applyPermitDefaultValues();
                corsConfiguration.setAllowedMethods(permittedCorsMethods);
                return corsConfiguration;

        }

        @Bean
        public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration)
                        throws Exception {
                return authenticationConfiguration.getAuthenticationManager();
        }

}
