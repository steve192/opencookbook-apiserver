package com.sterul.opencookbookapiserver.services.recipeimport.instagram;

import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;

import com.sterul.opencookbookapiserver.services.recipeimport.Links;

/**
 * A post as its page describes it to a link preview crawler.
 *
 * @param imageUrl null unless Instagram's own image servers serve it over https
 */
public record InstagramPost(String caption, String imageUrl) {

    /** Before the caption: "1,204 likes, 37 comments - account on March 3, 2026: ". */
    private static final Pattern COUNTS = Pattern.compile(
            "^[\\d.,]+[KkMm]? likes?(?:, [\\d.,]+[KkMm]? comments?)? - ");
    private static final Pattern ACCOUNT_AND_DATE = Pattern.compile(
            "^\\S+ on [^:\\n]+: \"(.*?)\"?\\.?\\s*$", Pattern.DOTALL);
    private static final Set<String> IMAGE_DOMAINS = Set.of("cdninstagram.com", "fbcdn.net");

    /** Empty for a private or deleted post, and for a page that asks to sign in. */
    public static Optional<InstagramPost> fromPage(String html) {
        var page = Jsoup.parse(html);
        var description = page.selectFirst("meta[property=og:description]");
        if (description == null) {
            return Optional.empty();
        }
        var caption = ACCOUNT_AND_DATE.matcher(COUNTS.matcher(description.attr("content")).replaceFirst(""));
        if (!caption.matches() || caption.group(1).isBlank()) {
            return Optional.empty();
        }
        var image = page.selectFirst("meta[property=og:image]");
        var imageUrl = image == null ? null : image.attr("content");
        return Optional.of(new InstagramPost(caption.group(1), isInstagramImage(imageUrl) ? imageUrl : null));
    }

    private static boolean isInstagramImage(String url) {
        return url != null && url.regionMatches(true, 0, "https://", 0, "https://".length())
                && Links.isOn(url, IMAGE_DOMAINS);
    }
}
