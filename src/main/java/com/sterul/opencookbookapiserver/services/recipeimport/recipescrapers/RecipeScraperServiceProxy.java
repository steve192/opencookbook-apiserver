package com.sterul.opencookbookapiserver.services.recipeimport.recipescrapers;

import com.sterul.opencookbookapiserver.configurations.OpencookbookConfiguration;
import com.sterul.opencookbookapiserver.services.recipeimport.ImportNotSupportedException;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class RecipeScraperServiceProxy {

    private static final HttpClientResponseHandler<String> READ_AS_UTF8_STRING =
            response -> EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);

    @Autowired
    OpencookbookConfiguration opencookbookConfiguration;

    public String scrapeRecipe(String url) throws IOException, ImportNotSupportedException {
        var request = new HttpGet(
                opencookbookConfiguration.getRecipeScaperServiceUrl() + "/api/v1/scrape-recipe?url=" + url);
        try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
            return httpclient.execute(request, response -> {
                if (response.getCode() == HttpStatus.SC_NOT_IMPLEMENTED) {
                    // ImportNotSupported is checked; HttpClientResponseHandler only allows
                    // IOException/HttpException, so signal via an IOException and unwrap below.
                    throw new ImportNotSupportedSignal();
                }
                return EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
            });
        } catch (ImportNotSupportedSignal e) {
            throw new ImportNotSupportedException();
        }
    }

    @Cacheable("recipe_scrapers_supported_hosts")
    public String getSupportedHosts() throws IOException {
        try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
            var request = new HttpGet(
                    opencookbookConfiguration.getRecipeScaperServiceUrl() + "/api/v1/scrape-recipe/supported-hosts");
            return httpclient.execute(request, READ_AS_UTF8_STRING);
        }
    }

    private static final class ImportNotSupportedSignal extends IOException {
    }
}
