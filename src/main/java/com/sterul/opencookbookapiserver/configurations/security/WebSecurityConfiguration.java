package com.sterul.opencookbookapiserver.configurations.security;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
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
            "/swagger-ui/*",
            "/v3/api-docs/*",
            "/api-docs*",
            "/api-docs/*",
            "/api-docs/*/*",
            "/api/v1/instance*",
            "/h2-console/*" };
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

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        List<String> permittedCorsMethods = Collections.unmodifiableList(Arrays.asList(
                HttpMethod.GET.name(),
                HttpMethod.HEAD.name(),
                HttpMethod.POST.name(),
                HttpMethod.PUT.name(),
                HttpMethod.DELETE.name()));

        var corsConfiguration = new CorsConfiguration().applyPermitDefaultValues();
        corsConfiguration.setAllowedMethods(permittedCorsMethods);

        http.cors().configurationSource(request -> corsConfiguration)
                // Allow frames needed for h2 console
                .and().headers().frameOptions().sameOrigin()

                // Disable csrf protection
                .and().csrf().disable()

                // Permit whitelist and authenticated request
                .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated()
                        .requestMatchers(AUTH_WHITELIST).permitAll())

                // Use own authentication manager (UserDetailsServiceImpl)
                .userDetailsService(userDetailsService)
                // Handler for when user is not authenticated
                .exceptionHandling().authenticationEntryPoint(unauthorizedEntryPoint)
                // Disable sessions
                .and().sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS);

        // Add a filter to check the sent token and authenticate
        http.addFilterBefore(jwtRequestFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

}
