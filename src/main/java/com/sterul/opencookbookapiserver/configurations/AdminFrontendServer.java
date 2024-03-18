package com.sterul.opencookbookapiserver.configurations;

import java.util.List;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;
import org.springframework.web.servlet.resource.ResourceResolverChain;

import jakarta.servlet.http.HttpServletRequest;

@Configuration
public class AdminFrontendServer implements WebMvcConfigurer {
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        this.serveDirectory(registry, "/admin", "classpath:/static/");
    }

    private void serveDirectory(ResourceHandlerRegistry registry, String endpoint, String location) {
         String[] endpointPatterns = endpoint.endsWith("/")
            ? new String[]{endpoint.substring(0, endpoint.length() - 1), endpoint, endpoint + "**"}
            : new String[]{endpoint, endpoint + "/", endpoint + "/**"};
    registry
            .addResourceHandler(endpointPatterns)
            .addResourceLocations(location.endsWith("/") ? location : location + "/")
            .resourceChain(false)
            .addResolver(new PathResourceResolver() {
                @Override
                public Resource resolveResource(HttpServletRequest request, String requestPath, List<? extends Resource> locations, ResourceResolverChain chain) {
                    Resource resource = super.resolveResource(request, requestPath, locations, chain);
                    if (resource != null) {
                        return resource;
                    }
                    return super.resolveResource(request, "/index.html", locations, chain);
                }
            });
    }

}