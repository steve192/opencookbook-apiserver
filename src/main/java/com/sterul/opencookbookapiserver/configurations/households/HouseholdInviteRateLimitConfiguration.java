package com.sterul.opencookbookapiserver.configurations.households;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.sterul.opencookbookapiserver.configurations.ratelimiting.PathVariableRateLimitInterceptor;
import com.sterul.opencookbookapiserver.controllers.households.HouseholdPaths;
import com.sterul.opencookbookapiserver.services.households.HouseholdInviteRateLimiter;

/** Applies the invite budget to everything that resolves a token. */
@Configuration
@ConditionalOnHouseholdsEnabled
public class HouseholdInviteRateLimitConfiguration implements WebMvcConfigurer {

    private final HouseholdInviteRateLimiter rateLimiter;

    public HouseholdInviteRateLimitConfiguration(HouseholdInviteRateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new PathVariableRateLimitInterceptor(
                        HouseholdPaths.INVITE_TOKEN_VARIABLE, rateLimiter::recordLookup, "household invite"))
                .addPathPatterns(HouseholdPaths.INVITE_PATTERN);
    }
}
