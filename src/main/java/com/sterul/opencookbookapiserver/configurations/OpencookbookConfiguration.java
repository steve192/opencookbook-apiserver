package com.sterul.opencookbookapiserver.configurations;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * Everything about this instance that an operator can set.
 */
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


    /**
     * Immediatly activate users upon signup. Useful for local development or where no mailserver is available
     */
    private boolean activateUsersAfterSignup = false;

    /**
     * Disables / enabled signups
     */
    private boolean allowSignup = true;

    /**
     * Settings for recipe sharing
     */
    private Sharing sharing = new Sharing();

    /**
     * Public sharing of recipes. Grouped rather than flat because every one of these values is
     * meaningless without the others: they describe how long a public link lives and how hard it
     * may be pulled while it does.
     */
    @Getter
    @Setter
    public static class Sharing {

        /**
         * Whether recipes can be shared at all. Turning this off takes the endpoints away, so
         * links already handed out stop resolving. Existing shares themselves are kept, and the
         * administration api still lists and removes them.
         */
        private boolean enabled = true;

        /**
         * How many days a share link stays valid. The clock starts when the link is created and
         * is never extended, so a link that is still in use lapses too - which is why the app
         * shows the owner when it will.
         */
        private int validityDays = 90;

        /**
         * How often one client may open shared recipes per hour. Aimed at a client walking many
         * share ids rather than at people reading one recipe. Sized for a household, where a
         * whole family shares one address.
         */
        private int viewsPerHourPerIp = 30;

        /**
         * How often a single share may be opened per hour, from anywhere. The backstop for a
         * leaked link, and the only limit that does not depend on a client supplied address.
         */
        private int viewsPerHourPerShare = 60;

        /**
         * How many images of shared recipes one client may load per hour. Counted separately
         * because a recipe with six pictures costs seven requests to render, so sharing a budget
         * with the recipe itself would make the effective limit depend on how photographed a
         * recipe happens to be.
         */
        private int imageViewsPerHourPerIp = 120;
    }

}