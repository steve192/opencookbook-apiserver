package com.sterul.opencookbookapiserver.services.legal;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import com.sterul.opencookbookapiserver.configurations.OpencookbookConfiguration;

import lombok.extern.slf4j.Slf4j;

/**
 * The operator's legal texts as HTML. Read once per document, so a changed file needs a restart.
 */
@Service
@Slf4j
public class LegalDocumentService {

    private final OpencookbookConfiguration configuration;

    public LegalDocumentService(OpencookbookConfiguration configuration) {
        this.configuration = configuration;
    }

    /** The document, or a placeholder naming the file the operator still has to provide. */
    @Cacheable("legal_documents")
    public String render(LegalDocument document) {
        var path = Path.of(configuration.getLegalDirectory(), document.fileName());
        return read(path)
                .filter(content -> !content.isBlank())
                .orElseGet(() -> placeholder(document, path));
    }

    private Optional<String> read(Path path) {
        if (!Files.isRegularFile(path)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readString(path, StandardCharsets.UTF_8));
        } catch (IOException e) {
            log.warn("Cannot read the legal document {}", path, e);
            return Optional.empty();
        }
    }

    private String placeholder(LegalDocument document, Path path) {
        return "<h1>" + document.getTitle() + "</h1>\n"
                + "<p>The operator of this instance has not published this document yet.</p>\n"
                + "<p>Operators: put it in <code>" + HtmlUtils.htmlEscape(path.toString())
                + "</code> and restart the server.</p>\n";
    }
}
