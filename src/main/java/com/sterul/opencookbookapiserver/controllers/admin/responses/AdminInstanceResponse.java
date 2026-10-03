package com.sterul.opencookbookapiserver.controllers.admin.responses;

import java.util.List;

import com.sterul.opencookbookapiserver.entities.instance.SignupMode;
import com.sterul.opencookbookapiserver.services.instance.InstanceOverview;

/** The configuration overview. Never carries a secret: no SMTP credentials, no ML token, no JWT data. */
public record AdminInstanceResponse(
        String version,
        InstanceUrl instanceUrl,
        Registration registration,
        Mail mail,
        Features features,
        Services services,
        Legal legal,
        Limits limits) {

    public record InstanceUrl(String configured, String effective) {
    }

    public record Registration(SignupMode signupMode, InstanceOverview.Confirmation confirmation) {
    }

    /** @param configured an SMTP host is set; mail is only sent when it is enabled as well, which needs the instance address */
    public record Mail(boolean configured, boolean enabled, String host, Integer port, String protocol,
            Boolean startTls, String from) {
    }

    public record Features(boolean sharing, boolean households, boolean apiKeys, RecipeScan recipeScan) {
    }

    public record RecipeScan(boolean configured, boolean enabled) {
    }

    public record Services(String recipeImportUrl, String recipeScanUrl) {
    }

    public record Legal(String directory, List<LegalDocument> documents) {
    }

    /** @param document as in the legal api's path: terms, privacy, imprint */
    public record LegalDocument(String document, boolean published) {
    }

    public record Limits(long maxUploadMb, long maxImageMb) {
    }

    public static AdminInstanceResponse of(InstanceOverview overview) {
        var mail = overview.mail();
        var features = overview.features();
        return new AdminInstanceResponse(
                overview.version(),
                new InstanceUrl(overview.instanceUrl().configured(), overview.instanceUrl().effective()),
                new Registration(overview.registration().signupMode(), overview.registration().confirmation()),
                new Mail(mail.configured(), mail.enabled(), mail.host(), mail.port(), mail.protocol(), mail.startTls(),
                        mail.from()),
                new Features(features.sharing(), features.households(), features.apiKeys(),
                        new RecipeScan(features.recipeScanConfigured(), features.recipeScanEnabled())),
                new Services(overview.services().recipeImportUrl(), overview.services().recipeScanUrl()),
                new Legal(overview.legal().directory(), overview.legal().documents().stream()
                        .map(state -> new LegalDocument(state.document().getPathSegment(), state.published()))
                        .toList()),
                new Limits(overview.limits().maxUploadMb(), overview.limits().maxImageMb()));
    }
}
