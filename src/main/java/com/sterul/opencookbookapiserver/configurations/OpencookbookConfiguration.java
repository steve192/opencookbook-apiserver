package com.sterul.opencookbookapiserver.configurations;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "opencookbook")
@Getter
@Setter
public class OpencookbookConfiguration {


    /**
     * URL where this instance is reachable (e.g. https://cookpal.io)
     */
    private String instanceURL = "";

    /**
     * Upload directory for images and co
     */
    private String uploadDir = "";

    /**
     * Directory for image thumbnails
     */
    private String thumbnailDir = "";

    /**
     * Maximum image size for image uploads in bytes
     */
    private Long maxImageSize;

    /**
     * Refresh token validity duration in seconds
     */
    private Long refreshTokenDuration;

    /**
     * JWT token validity duration in seconds
     */
    private Long jwtDuration;

    /**
     * Url of recipe scraper service
     */
    private String recipeScaperServiceUrl;

    /**
     * SMTP Host
     */
    private String smtpHost = "";

    /**
     * SMTP Port
     */
    private Integer smtpPort = 465;

    /**
     * SMTP Username
     */
    private String smtpUsername = "";

    /**
     * SMTP Password
     */
    private String smtpPassword = "";

    /**
     * SMTP Protocol (SMTP/SMTPS)
     */
    private String smtpProtocol = "smtps";

    /**
     * Use start tls? (true/false)
     */
    private String smtpStartTLS = "false";

    /**
     * Email address from which mails are sent from
     */
    private String mailFrom = "";

    /**
     * The location where the terms of service file is located
     */
    private String termsOfServiceFileLocation = "";

    /**
     * The width thumbnails are scaled down to (height is calculated by preserving width/height ratio)
     */
    private int imageScaleWidth = 1200;
    /**
     * The width images are scaled down to (height is calculated by preserving width/height ratio)
     */
    private int imageThumbnailScaleWidth = 512;

}