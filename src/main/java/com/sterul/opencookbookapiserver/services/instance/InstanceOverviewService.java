package com.sterul.opencookbookapiserver.services.instance;

import java.util.Optional;
import java.util.stream.Stream;

import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.servlet.autoconfigure.MultipartProperties;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import com.sterul.opencookbookapiserver.configurations.OpencookbookConfiguration;
import com.sterul.opencookbookapiserver.services.AppLinkFactory;
import com.sterul.opencookbookapiserver.services.google.GoogleClients;
import com.sterul.opencookbookapiserver.services.legal.LegalDocument;
import com.sterul.opencookbookapiserver.services.legal.LegalDocumentService;
import com.sterul.opencookbookapiserver.services.mail.MailAvailability;

/** Gathers what the environment configured, for an administrator. Leaves every secret out. */
@Service
public class InstanceOverviewService {

    private final OpencookbookConfiguration configuration;
    /** Absent when the jar was not built by Maven, as when running from an IDE. */
    private final Optional<BuildProperties> buildProperties;
    private final AppLinkFactory appLinks;
    private final InstanceSettingsService settings;
    private final MailAvailability mail;
    private final LegalDocumentService legalDocuments;
    private final MultipartProperties multipart;

    public InstanceOverviewService(OpencookbookConfiguration configuration, Optional<BuildProperties> buildProperties,
            AppLinkFactory appLinks, InstanceSettingsService settings, MailAvailability mail,
            LegalDocumentService legalDocuments, MultipartProperties multipart) {
        this.configuration = configuration;
        this.buildProperties = buildProperties;
        this.appLinks = appLinks;
        this.settings = settings;
        this.mail = mail;
        this.legalDocuments = legalDocuments;
        this.multipart = multipart;
    }

    public InstanceOverview overview() {
        var ml = configuration.getMl();
        return new InstanceOverview(
                buildProperties.map(BuildProperties::getVersion).orElse(null),
                new InstanceOverview.InstanceUrl(appLinks.configuredInstanceUrl().orElse(null), appLinks.instanceUrl()),
                new InstanceOverview.Registration(settings.getSignupMode(), mail.isEnabled()
                        ? InstanceOverview.Confirmation.MAIL
                        : InstanceOverview.Confirmation.ADMIN_APPROVAL),
                mail(),
                new InstanceOverview.Features(configuration.getSharing().isEnabled(),
                        configuration.getHouseholds().isEnabled(), configuration.getApiKeys().isEnabled(),
                        ml.isConfigured(), ml.getRecipeOcr().isEnabled()),
                new InstanceOverview.Services(withoutCredentials(configuration.getRecipeScaperServiceUrl()),
                        ml.isConfigured() ? withoutCredentials(ml.getServiceUrl()) : null),
                new InstanceOverview.Legal(configuration.getLegalDirectory(), Stream.of(LegalDocument.values())
                        .map(document -> new InstanceOverview.PublishedDocument(document,
                                legalDocuments.isPublished(document)))
                        .toList()),
                new InstanceOverview.Limits(multipart.getMaxRequestSize().toMegabytes(),
                        multipart.getMaxFileSize().toMegabytes()),
                GoogleClients.of(configuration.getAuth().getGoogle()).orElse(null));
    }

    private InstanceOverview.Mail mail() {
        var smtp = mail.hasSmtpHost();
        return new InstanceOverview.Mail(smtp, mail.isEnabled(), blankToNull(configuration.getSmtpHost()),
                smtp ? configuration.getSmtpPort() : null,
                smtp ? configuration.getSmtpProtocol() : null,
                smtp ? Boolean.parseBoolean(configuration.getSmtpStartTLS()) : null,
                blankToNull(configuration.getMailFrom()));
    }

    /** A service address may carry a login, which is not for the overview. */
    private static String withoutCredentials(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        return UriComponentsBuilder.fromUriString(url).userInfo(null).build().toUriString();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
