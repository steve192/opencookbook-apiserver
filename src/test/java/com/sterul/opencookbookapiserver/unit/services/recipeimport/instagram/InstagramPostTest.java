package com.sterul.opencookbookapiserver.unit.services.recipeimport.instagram;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.sterul.opencookbookapiserver.services.recipeimport.instagram.InstagramPost;

/** Reading the og tags of a post's page, as Instagram serves it to a link preview crawler. */
class InstagramPostTest {

    private static final String LINE_SEPARATOR = Character.toString(0x2028);
    private static final String PHOTO = "https://scontent-fra5-1.cdninstagram.com/v/t51/photo.jpg?stp=dst-jpg&amp;_nc_cat=1";

    @Test
    void theCaptionLosesTheCountsTheAccountAndTheQuotes() {
        var post = InstagramPost.fromPage(page(
                "1,204 likes, 37 comments - testkitchen on March 3, 2026: &quot;Apfelkuchen&#x2028;Zutaten: 3 &#xc4;pfel&quot;",
                PHOTO)).orElseThrow();

        assertEquals("Apfelkuchen" + LINE_SEPARATOR + "Zutaten: 3 Äpfel", post.caption());
    }

    @Test
    void theCountsMayBeMissing() {
        var post = InstagramPost.fromPage(page(
                "testkitchen on March 3, 2026: &quot;Zutaten:\n200 g Mehl&quot;", PHOTO)).orElseThrow();

        assertEquals("Zutaten:\n200 g Mehl", post.caption());
    }

    @Test
    void countsInThousandsAreCounts() {
        var post = InstagramPost.fromPage(page(
                "129K likes, 2,214 comments - testkitchen on July 1, 2025: &quot;Pancakes&quot;", PHOTO)).orElseThrow();

        assertEquals("Pancakes", post.caption());
    }

    @Test
    void quotesInsideTheCaptionStay() {
        var post = InstagramPost.fromPage(page(
                "5 likes, 0 comments - testkitchen on July 1, 2025: &quot;Comment &quot;RECIPE&quot; below&quot;", PHOTO))
                .orElseThrow();

        assertEquals("Comment \"RECIPE\" below", post.caption());
    }

    @Test
    void thePhotoIsTakenFromInstagramsImageServers() {
        var post = InstagramPost.fromPage(page("testkitchen on July 1, 2025: &quot;Pancakes&quot;", PHOTO))
                .orElseThrow();

        assertEquals("https://scontent-fra5-1.cdninstagram.com/v/t51/photo.jpg?stp=dst-jpg&_nc_cat=1", post.imageUrl());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "http://scontent.cdninstagram.com/photo.jpg",
        "https://example.com/photo.jpg",
        "https://cdninstagram.com.example.com/photo.jpg",
        "https://notcdninstagram.com/photo.jpg",
        "",
    })
    void aPhotoFromAnywhereElseIsLeftOut(String image) {
        var post = InstagramPost.fromPage(page("testkitchen on July 1, 2025: &quot;Pancakes&quot;", image))
                .orElseThrow();

        assertNull(post.imageUrl());
    }

    @Test
    void aPhotoOnTheFacebookImageServersIsInstagramsToo() {
        var post = InstagramPost.fromPage(page("testkitchen on July 1, 2025: &quot;Pancakes&quot;",
                "https://scontent.xx.fbcdn.net/photo.jpg")).orElseThrow();

        assertEquals("https://scontent.xx.fbcdn.net/photo.jpg", post.imageUrl());
    }

    @Test
    void aPageWithoutTheDescriptionHoldsNoPost() {
        assertTrue(InstagramPost.fromPage("<html><head><title>Instagram</title></head></html>").isEmpty());
    }

    @Test
    void aDescriptionThatIsNotAPostsHoldsNoPost() {
        var signIn = page("Create an account or log in to Instagram.", PHOTO);

        assertTrue(InstagramPost.fromPage(signIn).isEmpty());
    }

    @Test
    void anEmptyCaptionHoldsNoPost() {
        assertTrue(InstagramPost.fromPage(page("testkitchen on July 1, 2025: &quot;&quot;", PHOTO)).isEmpty());
    }

    private static String page(String description, String image) {
        return """
                <!DOCTYPE html>
                <html><head>
                <meta property="og:title" content="Test Kitchen on Instagram" />
                <meta property="og:image" content="%s" />
                <meta property="og:description" content="%s" />
                </head><body></body></html>
                """.formatted(image, description);
    }
}
