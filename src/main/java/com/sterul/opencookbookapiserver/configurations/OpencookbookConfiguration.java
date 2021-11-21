package com.sterul.opencookbookapiserver.configurations;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;

@ConfigurationProperties("opencookbook")
@Getter
public class OpencookbookConfiguration {

    /**
     * Upload directory for images and co
     */
    private String uploadDir;

    /**
     * Maximum image size for image uploads
     */
    private String maxImageSize;

}