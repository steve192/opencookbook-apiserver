package com.sterul.opencookbookapiserver.configurations.security;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfiguration;

import com.sterul.opencookbookapiserver.configurations.security.requestfilters.JwtRequestFilter;

import jakarta.servlet.http.HttpServletRequest;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class WebSecurityConfiguration {

    private static final List<String> AUTH_WHITELIST = Arrays.asList(
            "/api/v1/users/signup",
            "/api/v1/users/activate",
            "/api/v1/users/resendActivationLink",
            "/api/v1/users/requestPasswordReset",
            "/api/v1/users/resetPassword",
            "/api/v1/users/login",
            "/api/v1/users/refreshToken",
            "/swagger-ui/*",
            "/v3/api-docs/*",
            "/api-docs*",
            "/api-docs/*",
            "/api-docs/*/*",
            "/api/v1/instance*",
            "/h2-console/*");
    @Autowired
    private UserDetailsService userDetailsService;
    @Autowired
    private UnauthorizedEntryPoint unauthorizedEntryPoint;
    @Autowired
    private JwtRequestFilter jwtRequestFilter;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    public void configureGlobal(AuthenticationManagerBuilder auth) throws Exception {
        // Configure service to check if credentials are valid
        auth.userDetailsService(userDetailsService).passwordEncoder(passwordEncoder);
    }

    private RequestMatcher allowedPathRequestMatcher() {
        return new RequestMatcher() {

            @Override
            public boolean matches(HttpServletRequest request) {
                return AUTH_WHITELIST.contains(request.getServletPath());
            }

        };
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        // Cors and csrf not needed in an api server
        http.cors(configurer -> configurer.disable());
        http.csrf(conf -> conf.disable());

        // Allow frames needed for h2 console
        http.headers(config -> config.frameOptions(options -> options.sameOrigin()));

        // Permit whitelist and authenticated request
        http.authorizeHttpRequests(
                authorize -> {
                    authorize.requestMatchers(allowedPathRequestMatcher()).permitAll();
                    authorize.anyRequest().authenticated();
                });

        http.exceptionHandling(configurer -> configurer
                .authenticationEntryPoint(unauthorizedEntryPoint));

        // Disable sessions since auth/session token is passed in every request
        http.sessionManagement(configurer -> configurer.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        // Add a filter to check the sent token and authenticate
        http.addFilterBefore(jwtRequestFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(HttpSecurity http)
            throws Exception {
        return http.getSharedObject(AuthenticationManagerBuilder.class)
                .userDetailsService(userDetailsService)
                .passwordEncoder(passwordEncoder).and().build();
    }

}
