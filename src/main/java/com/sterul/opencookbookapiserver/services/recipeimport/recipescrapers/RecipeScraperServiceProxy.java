package com.sterul.opencookbookapiserver.services.recipeimport.recipescrapers;

import java.io.IOException;

import com.sterul.opencookbookapiserver.configurations.OpencookbookConfiguration;

import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class RecipeScraperServiceProxy {

    @Autowired
    OpencookbookConfiguration opencookbookConfiguration;

    public String scrapeRecipe(String url) throws IOException {
        var client = HttpClientBuilder.create().build();
        var request = new HttpGet(
                opencookbookConfiguration.getRecipeScaperServiceUrl() + "/api/v1/scrape-recipe?url=" + url);
        var response = client.execute(request);
        return EntityUtils.toString(response.getEntity(), "UTF-8");
    }
}