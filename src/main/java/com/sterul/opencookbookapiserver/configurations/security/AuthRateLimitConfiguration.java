package com.sterul.opencookbookapiserver.configurations.security;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.sterul.opencookbookapiserver.services.AuthRateLimiter;

/**
 * Counts what is done at the endpoints that need no token: the only ones a stranger can reach,
 * and two of them make the server send mail.
 */
@Configuration
public class AuthRateLimitConfiguration implements WebMvcConfigurer {

    private final AuthRateLimiter rateLimiter;

    public AuthRateLimitConfiguration(AuthRateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new AuthRateLimitInterceptor(rateLimiter))
                .addPathPatterns(WebSecurityConfiguration.UNAUTHENTICATED_USER_PATHS);
    }
}
