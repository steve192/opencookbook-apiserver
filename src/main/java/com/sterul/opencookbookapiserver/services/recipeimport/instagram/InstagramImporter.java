package com.sterul.opencookbookapiserver.services.recipeimport.instagram;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.util.Timeout;
import org.springframework.stereotype.Component;

import com.sterul.opencookbookapiserver.configurations.OpencookbookConfiguration;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.entities.recipe.Recipe;
import com.sterul.opencookbookapiserver.errors.ApiErrorCode;
import com.sterul.opencookbookapiserver.errors.ApiException;
import com.sterul.opencookbookapiserver.services.RecipeImageService;
import com.sterul.opencookbookapiserver.services.recipeimport.AbstractRecipeImporter;
import com.sterul.opencookbookapiserver.services.recipeimport.ImportNotSupportedException;
import com.sterul.opencookbookapiserver.services.recipeimport.text.RecipeTextImporter;

/** A post's caption read as a recipe text, with the post's photo: a draft for the editor. */
@Component
public class InstagramImporter extends AbstractRecipeImporter {

    /** Instagram answers a link preview crawler without a sign in. */
    private static final String CRAWLER_USER_AGENT =
            "facebookexternalhit/1.1 (+http://www.facebook.com/externalhit_uatext.php)";
    private static final Timeout TIMEOUT = Timeout.ofSeconds(10);
    private static final int MAX_PAGE_SIZE = 4 * 1024 * 1024;
    /** "/p/CODE/", "/reel/CODE/", "/reels/CODE/", "/tv/CODE/", also below the account's name. */
    private static final Pattern POST_PATH = Pattern.compile("^/(?:[\\w.]+/)?(?:p|reels?|tv)/([\\w-]+)");

    private final RecipeTextImporter textImporter;
    private final InstagramReadRateLimiter rateLimiter;

    public InstagramImporter(RecipeImageService recipeImageService, OpencookbookConfiguration opencookbookConfiguration,
            RecipeTextImporter textImporter, InstagramReadRateLimiter rateLimiter) {
        super(recipeImageService, opencookbookConfiguration);
        this.textImporter = textImporter;
        this.rateLimiter = rateLimiter;
    }

    @Override
    public Recipe importRecipe(String url, CookpalUser owner) {
        var postUrl = postUrlOf(url);
        if (!rateLimiter.recordRead(owner).allowed()) {
            throw new ApiException(ApiErrorCode.RATE_LIMITED, "Instagram read rate limit exceeded");
        }
        var post = InstagramPost.fromPage(fetchPage(postUrl)).orElseThrow(() -> new ApiException(
                ApiErrorCode.IMPORT_SOURCE_UNAVAILABLE, "No caption on " + postUrl));
        var draft = textImporter.importText(post.caption(), owner);
        draft.setRecipeSource(postUrl);
        addImage(draft, post.imageUrl(), owner);
        return draft;
    }

    @Override
    public List<String> getSupportedHostnames() {
        return List.of("instagram.com");
    }

    @Override
    public boolean needsReview() {
        return true;
    }

    /** Rebuilt from the post's code, so that the link cannot lead the server anywhere else. */
    private static String postUrlOf(String url) {
        var path = POST_PATH.matcher(String.valueOf(URI.create(url).getPath()));
        if (!path.find()) {
            throw new ImportNotSupportedException();
        }
        return "https://www.instagram.com/p/" + path.group(1) + "/";
    }

    private String fetchPage(String postUrl) {
        var request = new HttpGet(postUrl);
        request.setHeader(HttpHeaders.USER_AGENT, CRAWLER_USER_AGENT);
        // A post that is not public redirects to the sign in.
        request.setConfig(RequestConfig.custom().setConnectTimeout(TIMEOUT).setResponseTimeout(TIMEOUT)
                .setRedirectsEnabled(false).build());
        try {
            return client.execute(request, response -> {
                if (response.getCode() != HttpStatus.SC_OK) {
                    throw new IOException("Instagram answered " + response.getCode());
                }
                return EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8, MAX_PAGE_SIZE);
            });
        } catch (IOException e) {
            throw new ApiException(ApiErrorCode.IMPORT_SOURCE_UNAVAILABLE, "Could not read " + postUrl, e);
        }
    }
}
