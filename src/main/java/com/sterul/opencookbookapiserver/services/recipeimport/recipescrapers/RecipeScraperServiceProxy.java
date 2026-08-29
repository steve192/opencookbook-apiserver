package com.sterul.opencookbookapiserver.services.recipeimport.recipescrapers;

import com.sterul.opencookbookapiserver.configurations.OpencookbookConfiguration;
import com.sterul.opencookbookapiserver.services.recipeimport.ImportNotSupportedException;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class RecipeScraperServiceProxy {

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

    public String scrapeRecipe(String url) throws IOException, ImportNotSupportedException {
        var response = get("/api/v1/scrape-recipe?url=" + url);
        if (response.statusCode() == HttpStatus.SC_NOT_IMPLEMENTED) {
            throw new ImportNotSupportedException();
        }
        return response.body();
    }

    @Cacheable("recipe_scrapers_supported_hosts")
    public String getSupportedHosts() throws IOException {
        return get("/api/v1/scrape-recipe/supported-hosts").body();
    }

    private ScrapeResponse get(String path) throws IOException {
        var request = new HttpGet(opencookbookConfiguration.getRecipeScaperServiceUrl() + path);
        try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
            return httpclient.execute(request, RESPONSE_HANDLER);
        }
    }
}
