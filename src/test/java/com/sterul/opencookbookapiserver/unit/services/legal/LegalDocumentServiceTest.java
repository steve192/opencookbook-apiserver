package com.sterul.opencookbookapiserver.unit.services.legal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sterul.opencookbookapiserver.configurations.OpencookbookConfiguration;
import com.sterul.opencookbookapiserver.services.legal.LegalDocument;
import com.sterul.opencookbookapiserver.services.legal.LegalDocumentService;

class LegalDocumentServiceTest {

    @TempDir
    Path directory;

    private LegalDocumentService cut() {
        var configuration = new OpencookbookConfiguration();
        configuration.setLegalDirectory(directory.toString());
        return new LegalDocumentService(configuration);
    }

    @Test
    void aPresentFileIsServedAsItIs() throws Exception {
        Files.writeString(directory.resolve("privacy.html"), "<h1>Datenschutz äöü</h1>", StandardCharsets.UTF_8);

        assertEquals("<h1>Datenschutz äöü</h1>", cut().render(LegalDocument.PRIVACY));
    }

    @Test
    void aMissingFileShowsAPlaceholderNamingTheExpectedPath() {
        var html = cut().render(LegalDocument.IMPRINT);

        assertTrue(html.contains("<h1>Imprint</h1>"));
        assertTrue(html.contains("not published"));
        assertTrue(html.contains(directory.resolve("imprint.html").toString()));
    }

    @Test
    void aBlankFileShowsAPlaceholderToo() throws Exception {
        Files.writeString(directory.resolve("terms.html"), " \n\t\n");

        assertTrue(cut().render(LegalDocument.TERMS).contains("<h1>Terms of use</h1>"));
    }

    @Test
    void thePathInThePlaceholderIsEscaped() {
        var configuration = new OpencookbookConfiguration();
        configuration.setLegalDirectory(directory.resolve("<b>").toString());

        var html = new LegalDocumentService(configuration).render(LegalDocument.TERMS);

        assertTrue(html.contains("&lt;b&gt;"));
        assertFalse(html.contains("<b>"));
    }

    @Test
    void aDocumentIsFoundByItsPathSegment() {
        assertEquals(LegalDocument.PRIVACY, LegalDocument.byPathSegment("privacy").orElseThrow());
        assertTrue(LegalDocument.byPathSegment("PRIVACY").isEmpty());
        assertEquals("terms.html", LegalDocument.TERMS.fileName());
    }
}
