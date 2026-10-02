package com.sterul.opencookbookapiserver.controllers.legal;

import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

import com.sterul.opencookbookapiserver.services.legal.LegalDocument;

/** Binds the path segment, not the constant name, to the document. */
@Component
class LegalDocumentConverter implements Converter<String, LegalDocument> {

    @Override
    public LegalDocument convert(String pathSegment) {
        return LegalDocument.byPathSegment(pathSegment)
                .orElseThrow(() -> new IllegalArgumentException("No such legal document: " + pathSegment));
    }
}
