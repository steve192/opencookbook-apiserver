package com.sterul.opencookbookapiserver.configurations.sharing;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.sterul.opencookbookapiserver.controllers.sharing.SharePaths;
import com.sterul.opencookbookapiserver.services.sharing.ShareAccessRateLimiter;

/**
 * Applies the share budgets to the public endpoints.
 *
 * Recipes and images are registered separately rather than sorted out inside the interceptor: a
 * single asterisk does not cross a slash, so the two patterns are disjoint by construction and
 * neither has to know that the other exists.
 */
@Configuration
public class ShareRateLimitConfiguration implements WebMvcConfigurer {

    private final ShareAccessRateLimiter rateLimiter;

    public ShareRateLimitConfiguration(ShareAccessRateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new ShareAccessRateLimitInterceptor(rateLimiter::recordRecipeView))
                .addPathPatterns(SharePaths.PUBLIC_RECIPE_PATTERN);
        registry.addInterceptor(new ShareAccessRateLimitInterceptor(rateLimiter::recordImageView))
                .addPathPatterns(SharePaths.PUBLIC_IMAGE_PATTERN);
    }
}
