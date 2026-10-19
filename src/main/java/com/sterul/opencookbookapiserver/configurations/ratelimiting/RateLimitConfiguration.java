package com.sterul.opencookbookapiserver.configurations.ratelimiting;

import java.time.Clock;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.sterul.opencookbookapiserver.configurations.OpencookbookConfiguration;
import com.sterul.opencookbookapiserver.services.SignedInUserService;

/** One interceptor for every endpoint; only those annotated with {@code @RateLimited} spend a budget. */
@Configuration
public class RateLimitConfiguration implements WebMvcConfigurer {

    private final RateLimitInterceptor interceptor;

    public RateLimitConfiguration(OpencookbookConfiguration configuration, SignedInUserService signedInUser, Clock clock) {
        this.interceptor = new RateLimitInterceptor(configuration, signedInUser, clock);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor);
    }
}
