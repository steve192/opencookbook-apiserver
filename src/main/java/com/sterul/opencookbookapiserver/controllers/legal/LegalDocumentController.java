package com.sterul.opencookbookapiserver.controllers.legal;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.sterul.opencookbookapiserver.controllers.BaseController;
import com.sterul.opencookbookapiserver.services.SignedInUserService;
import com.sterul.opencookbookapiserver.services.legal.LegalDocument;
import com.sterul.opencookbookapiserver.services.legal.LegalDocumentService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@Tag(name = "Legal")
public class LegalDocumentController extends BaseController {

    private final LegalDocumentService documents;

    public LegalDocumentController(LegalDocumentService documents, SignedInUserService signedInUser) {
        super(signedInUser);
        this.documents = documents;
    }

    @Operation(summary = "A legal text of this instance: terms, privacy or imprint",
            description = "HTML, public. A placeholder until the operator has published it.")
    @GetMapping(value = LegalPaths.BASE + "/{document}",
            produces = MediaType.TEXT_HTML_VALUE + ";charset=UTF-8")
    public String getDocument(@PathVariable LegalDocument document) {
        return documents.render(document);
    }
}
