package com.sterul.opencookbookapiserver.unit.services.recipeimport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.sterul.opencookbookapiserver.services.recipeimport.Links;

/** Whether what somebody pasted or shared is a link to import, or a text to read a recipe from. */
class LinksTest {

    private static final String LINK = "https://example.com/apple-pie";
    private static final String EM_DASH = Character.toString(0x2014);

    @ParameterizedTest
    @ValueSource(strings = {
        LINK,
        "  " + LINK + "  ",
        "My Apple Pie\n" + LINK,
        "My Apple Pie - " + LINK,
        "My Apple Pie | Example Kitchen: " + LINK,
        "\"My Apple Pie\" " + LINK,
        "Look at this: " + LINK + ".",
    })
    void aLinkWithAtMostATitleBesideItIsALink(String shared) {
        assertEquals(Optional.of(LINK), Links.shared(shared));
    }

    @Test
    void aTitleWithAnEmDashIsStillATitle() {
        assertEquals(Optional.of(LINK), Links.shared("My Apple Pie " + EM_DASH + " " + LINK));
    }

    @Test
    void instagramSharesItsLinkWithTrackingParameters() {
        var shared = "https://www.instagram.com/reel/AbC_12-x/?igsh=MWQ1ZGUxMzBkMA==";

        assertEquals(Optional.of(shared), Links.shared(shared));
    }

    @Test
    void aRecipeThatEndsWithItsSourceIsText() {
        var recipe = """
                Apple pie

                Ingredients:
                3 apples
                200 g flour

                Source: https://example.com/apple-pie
                """;

        assertTrue(Links.shared(recipe).isEmpty());
    }

    @Test
    void textWithoutALinkIsText() {
        assertTrue(Links.shared("200 g Mehl\n2 Eier").isEmpty());
    }

    @Test
    void twoLinksAreText() {
        var input = LINK + " " + "https://example.com/pear-pie";

        assertTrue(Links.shared(input).isEmpty());
    }

    @Test
    void aLongLineBesideTheLinkIsText() {
        var input = "x".repeat(121) + " " + LINK;

        assertTrue(Links.shared(input).isEmpty());
    }
}
