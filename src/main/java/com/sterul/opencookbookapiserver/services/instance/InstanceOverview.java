package com.sterul.opencookbookapiserver.services.instance;

import java.util.List;

import com.sterul.opencookbookapiserver.entities.instance.SignupMode;
import com.sterul.opencookbookapiserver.services.google.GoogleClients;
import com.sterul.opencookbookapiserver.services.legal.LegalDocument;

/**
 * How this instance is configured, without a single secret.
 *
 * @param googleSignIn null when the instance does not offer signing in with Google
 */
public record InstanceOverview(
        String version,
        InstanceUrl instanceUrl,
        Registration registration,
        Mail mail,
        Features features,
        Services services,
        Legal legal,
        Limits limits,
        GoogleClients googleSignIn) {

    /** How an open signup is confirmed. */
    public enum Confirmation {
        MAIL, ADMIN_APPROVAL
    }

    /** @param configured null when unset; {@code effective} is then the address the request came in on */
    public record InstanceUrl(String configured, String effective) {
    }

    public record Registration(SignupMode signupMode, Confirmation confirmation) {
    }

    /**
     * @param configured an SMTP host is set
     * @param enabled    mail is sent: an SMTP host and the instance address are both set
     * @param port       null unless an SMTP host is set, as are the protocol and startTls
     */
    public record Mail(boolean configured, boolean enabled, String host, Integer port, String protocol,
            Boolean startTls, String from) {
    }

    public record Features(boolean sharing, boolean households, boolean apiKeys, boolean recipeScanConfigured,
            boolean recipeScanEnabled) {
    }

    /** @param recipeScanUrl null when no subsystem is configured */
    public record Services(String recipeImportUrl, String recipeScanUrl) {
    }

    public record Legal(String directory, List<PublishedDocument> documents) {
    }

    public record PublishedDocument(LegalDocument document, boolean published) {
    }

    public record Limits(long maxUploadMb, long maxImageMb) {
    }
}
