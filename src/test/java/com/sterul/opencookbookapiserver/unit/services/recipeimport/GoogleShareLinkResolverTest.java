package com.sterul.opencookbookapiserver.unit.services.recipeimport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import com.sterul.opencookbookapiserver.services.recipeimport.GoogleShareLinkResolver;

class GoogleShareLinkResolverTest {

    private static final String SHARE_LINK = "https://share.google/AbC123xyz";
    private static final String LOOKUP = "https://www.google.com/share.google?q=AbC123xyz";
    private static final String PAGE = "https://recipes.example.com/apple-pie?id=7";

    private StubbedHttp http;
    private GoogleShareLinkResolver cut;

    @BeforeEach
    void setup() throws IOException {
        http = new StubbedHttp().redirect(SHARE_LINK, LOOKUP).redirect(LOOKUP, PAGE);
        cut = new GoogleShareLinkResolver();
        ReflectionTestUtils.setField(cut, "client", http.client());
    }

    @Test
    void aShareLinkLeadsToThePageBehindIt() {
        assertEquals(PAGE, cut.resolve(SHARE_LINK));
    }

    @Test
    void thePageItselfIsNeverFetched() {
        cut.resolve(SHARE_LINK);

        assertEquals(2, http.requests().size());
        assertTrue(http.requests().stream().noneMatch(request -> request.getRequestUri().contains("apple-pie")));
    }

    @ParameterizedTest
    @ValueSource(strings = { "http://share.google/AbC123xyz", "https://SHARE.GOOGLE/AbC123xyz/" })
    void theShareLinkIsRebuiltFromItsCode(String link) {
        assertEquals(PAGE, cut.resolve(link));
    }

    @ParameterizedTest
    @ValueSource(strings = { PAGE, "https://www.instagram.com/p/AbC/", "https://www.google.com/search?q=pie" })
    void anyOtherLinkStaysAsItIs(String link) {
        assertEquals(link, cut.resolve(link));
        assertTrue(http.requests().isEmpty());
    }

    @Test
    void anUnknownCodeStaysAsItIs() {
        http.redirect(LOOKUP, "https://share.google/error").page("https://share.google/error", "<html></html>");

        assertEquals(SHARE_LINK, cut.resolve(SHARE_LINK));
    }

    @Test
    void redirectsThatNeverLeaveGoogleLeaveTheLinkAsItIs() {
        http.redirect(LOOKUP, SHARE_LINK);

        assertEquals(SHARE_LINK, cut.resolve(SHARE_LINK));
    }

    @Test
    void aShareLinkWithoutACodeStaysAsItIs() {
        assertEquals("https://share.google/a/b", cut.resolve("https://share.google/a/b"));
        assertTrue(http.requests().isEmpty());
    }

    @Test
    void whenGoogleDoesNotAnswerTheLinkStaysAsItIs() {
        assertEquals("https://share.google/Unstubbed", cut.resolve("https://share.google/Unstubbed"));
    }
}
