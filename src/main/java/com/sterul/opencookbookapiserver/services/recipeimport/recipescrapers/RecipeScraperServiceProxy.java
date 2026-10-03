package com.sterul.opencookbookapiserver.services.recipeimport.recipescrapers;

import com.sterul.opencookbookapiserver.configurations.OpencookbookConfiguration;
import com.sterul.opencookbookapiserver.services.recipeimport.ImportNotSupportedException;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.util.Timeout;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Component
public class RecipeScraperServiceProxy {

    private static final String SUPPORTED_HOSTS_PATH = "/api/v1/scrape-recipe/supported-hosts";

    /**
     * Captures the parts of the response the callers need, so that status handling
     * happens after the connection has been released instead of inside the handler.
     */
    private record ScrapeResponse(int statusCode, String body) {
    }

    private static final HttpClientResponseHandler<ScrapeResponse> RESPONSE_HANDLER = response -> new ScrapeResponse(
            response.getCode(), EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8));

    private final OpencookbookConfiguration opencookbookConfiguration;

    public RecipeScraperServiceProxy(OpencookbookConfiguration opencookbookConfiguration) {
        this.opencookbookConfiguration = opencookbookConfiguration;
    }

    public String scrapeRecipe(String url) throws IOException {
        // Encoded rather than concatenated: a recipe link carrying its own query string ("?id=7")
        // used to have everything after the first "&" read as further parameters of this call,
        // which both truncated the link and let a caller add parameters of their own to it.
        var response = get("/api/v1/scrape-recipe?url="
                + URLEncoder.encode(url, StandardCharsets.UTF_8));
        if (response.statusCode() == HttpStatus.SC_NOT_IMPLEMENTED) {
            throw new ImportNotSupportedException();
        }
        return response.body();
    }

    @Cacheable("recipe_scrapers_supported_hosts")
    public String getSupportedHosts() throws IOException {
        return get(SUPPORTED_HOSTS_PATH).body();
    }

    /**
     * Asks the service right now, uncached and quickly, for an administrator checking it.
     *
     * @return the http status it answered with
     */
    public int probe(Duration timeout) throws IOException {
        var request = request(SUPPORTED_HOSTS_PATH);
        var limit = Timeout.of(timeout);
        request.setConfig(RequestConfig.custom().setConnectTimeout(limit).setResponseTimeout(limit).build());
        return execute(request).statusCode();
    }

    private ScrapeResponse get(String path) throws IOException {
        return execute(request(path));
    }

    private HttpGet request(String path) {
        return new HttpGet(opencookbookConfiguration.getRecipeScaperServiceUrl() + path);
    }

    private static ScrapeResponse execute(HttpGet request) throws IOException {
        try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
            return httpclient.execute(request, RESPONSE_HANDLER);
        }
    }
}
