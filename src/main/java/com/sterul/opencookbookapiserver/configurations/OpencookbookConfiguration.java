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

    /** Shared cookbooks and shared weekplans. */
    private Households households = new Households();

    /**
     * What may be done by somebody who is not signed in yet.
     */
    private Auth auth = new Auth();

    /** Nutrition estimation from the shipped food catalogue. */
    private Nutrition nutrition = new Nutrition();

    /**
     * Connection to the machine learning subsystem. Leaving the url empty is how an instance
     * says it has none, and every feature that would need one disappears rather than failing.
     */
    private Ml ml = new Ml();

    /**
     * The optional machine learning subsystem: a separate service that runs OCR and, later,
     * other model-backed work. It is reached with an api token held only here, so the app
     * never learns that it exists.
     */
    @Getter
    @Setter
    public static class Ml {

        /**
         * Where the subsystem is reachable (e.g. https://ml.cookpal.io). Empty means this
         * instance has no subsystem, which is the default and a supported way to run cookpal.
         */
        private String serviceUrl = "";

        /**
         * The api token issued for this instance. Never leaves the server.
         */
        private String apiToken = "";

        /**
         * Salt used to turn a user into the opaque id the subsystem groups their jobs by. It
         * must stay stable for as long as donated training data is kept, because changing it
         * makes earlier donations unmatchable to a later "delete everything of mine".
         *
         * Left empty it is derived from the api token, which is already per-instance and
         * secret. Set it explicitly on an instance that expects to rotate that token.
         */
        private String submitterSalt = "";

        /**
         * How long to wait for a connection to the subsystem before giving up.
         */
        private int connectTimeoutSeconds = 5;

        /**
         * How long a single request to the subsystem may take. Submitting an image is the
         * slow one, because the bytes travel with it.
         */
        private int requestTimeoutSeconds = 30;

        /**
         * How often we ask the subsystem whether a job has finished.
         */
        private int pollIntervalSeconds = 2;

        /**
         * After this long an unfinished job is abandoned. Without it a subsystem that
         * silently loses work would leave a user watching a spinner for ever.
         */
        private int jobTimeoutSeconds = 300;

        /**
         * How long a finished job is kept before it is deleted. Long enough that the app can
         * still collect a result after a restart, short enough that we are not storing
         * everybody's recipes twice.
         */
        private int jobRetentionHours = 24;

        /**
         * Settings for reading a recipe from a photograph.
         */
        private RecipeOcr recipeOcr = new RecipeOcr();

        /**
         * Whether this instance has a subsystem at all. The url is the switch, so that there
         * is no second flag that can disagree with it.
         */
        public boolean isConfigured() {
            return serviceUrl != null && !serviceUrl.isBlank();
        }

        /**
         * The salt actually in use, falling back to the api token so that there is no way to
         * configure a subsystem and accidentally leave the derivation unkeyed.
         */
        public String effectiveSubmitterSalt() {
            if (submitterSalt != null && !submitterSalt.isBlank()) {
                return submitterSalt;
            }
            // "apiToken:" with no value binds to null, not to the empty default above.
            return apiToken == null ? "" : apiToken;
        }

        @Getter
        @Setter
        public static class RecipeOcr {

            /**
             * Whether photographed recipes may be imported. Off takes the endpoints away.
             */
            private boolean enabled = true;

            /**
             * How many recipes one user may scan per day. The instance's own allowance with
             * the subsystem is finite and shared, so one user must not be able to spend it.
             */
            private int jobsPerUserPerDay = 20;

            /**
             * How many photographs may make up one recipe.
             */
            private int maxPages = 6;
        }
    }

    @Getter
    @Setter
    public static class Nutrition {

        /** Off: no catalogue import and no nutrition endpoints. The schema exists either way. */
        private boolean enabled = false;
    }

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

    /** Households: small private groups sharing a cookbook and a weekplan. The caps keep them small. */
    @Getter
    @Setter
    public static class Households {

        /** Off removes the endpoints and stops members reading each other's cookbooks; rows are kept. */
        private boolean enabled = true;

        /** How many people one household may hold. */
        private int maxMembers = 10;

        /** How many households one account may be in. */
        private int maxPerUser = 5;

        /** How many unexpired invites one household may have outstanding. */
        private int maxLiveInvites = 5;

        /** How many days an invite link stays valid. */
        private int inviteValidityDays = 14;

        /** How often one client may resolve invite tokens per hour. */
        private int inviteLookupsPerHourPerIp = 30;
    }

    /**
     * Budgets for the endpoints that need no token: reachable by anybody, and two of them make
     * the server send e-mail.
     */
    @Getter
    @Setter
    public static class Auth {

        /**
         * How many calls to the endpoints that need no token one address may make per hour.
         * Generous on purpose: signing in is rare, and a household or a mobile network can put
         * a great many people behind one address.
         */
        private int attemptsPerHourPerIp = 60;

        /**
         * How many activation or password reset mails one address may be sent per hour, whoever
         * asks. Counted per recipient, not per caller: a caller with many addresses of their own
         * would slip a per-caller budget.
         */
        private int mailsPerHourPerAddress = 5;
    }

}
