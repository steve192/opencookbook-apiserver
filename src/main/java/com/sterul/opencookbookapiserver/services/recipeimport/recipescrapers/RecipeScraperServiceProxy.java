package com.sterul.opencookbookapiserver.services.recipeimport.recipescrapers;

import com.sterul.opencookbookapiserver.configurations.OpencookbookConfiguration;
import com.sterul.opencookbookapiserver.services.recipeimport.ImportNotSupportedException;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class RecipeScraperServiceProxy {

    @Autowired
    OpencookbookConfiguration opencookbookConfiguration;

    public String scrapeRecipe(String url) throws IOException, ImportNotSupportedException {

        var request = new HttpGet(
                opencookbookConfiguration.getRecipeScaperServiceUrl() + "/api/v1/scrape-recipe?url=" + url);
        try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
            var response = httpclient.execute(request);
            if (response.getStatusLine().getStatusCode() == HttpStatus.SC_NOT_IMPLEMENTED) {
                throw new ImportNotSupportedException();
            }
            return EntityUtils.toString(response.getEntity(), "UTF-8");
        }
    }

    @Cacheable("recipe_scrapers_supported_hosts")
    public String getSupportedHosts() throws IOException {
        try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
            var response = httpclient.execute(new HttpGet(
                    opencookbookConfiguration.getRecipeScaperServiceUrl() + "/api/v1/scrape-recipe/supported-hosts"));
            return EntityUtils.toString(response.getEntity(), "UTF-8");
        }
    }
}