package com.sterul.opencookbookapiserver.services.legal;

import java.util.Arrays;
import java.util.Optional;

import lombok.Getter;

/** The texts an instance operator has to publish: terms, privacy policy and imprint. */
@Getter
public enum LegalDocument {

    TERMS("terms", "Terms of use"),
    PRIVACY("privacy", "Privacy policy"),
    IMPRINT("imprint", "Imprint");

    private final String pathSegment;
    private final String title;

    LegalDocument(String pathSegment, String title) {
        this.pathSegment = pathSegment;
        this.title = title;
    }

    public String fileName() {
        return pathSegment + ".html";
    }

    public static Optional<LegalDocument> byPathSegment(String pathSegment) {
        return Arrays.stream(values())
                .filter(document -> document.pathSegment.equals(pathSegment))
                .findFirst();
    }
}
