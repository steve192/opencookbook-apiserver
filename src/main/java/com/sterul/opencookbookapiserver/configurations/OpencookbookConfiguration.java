package com.sterul.opencookbookapiserver.configurations;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Getter;
import lombok.Setter;

@Configuration
@ConfigurationProperties(prefix = "opencookbook")
@Getter
@Setter
public class OpencookbookConfiguration {

    /**
     * Upload directory for images and co
     */
    private String uploadDir;

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
     *  SMTP Host
     */
    private String smtpHost="";

    /**
     * SMTP Port
     */
    private Integer smtpPort=465;

    /**
     * SMTP Username
     */
    private String smtpUsername="";

    /**
     * SMTP Password
     */
    private String smtpPassword="";

    /**
     * SMTP Protocol (SMTP/SSMTP)
     */
    private String smtpProtocol="smtp";

    /**
     * Use start tls? (true/false)
     */
    private String smtpStartTLS="false";

}