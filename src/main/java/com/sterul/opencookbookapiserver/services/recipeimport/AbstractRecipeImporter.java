package com.sterul.opencookbookapiserver.services.recipeimport;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import com.google.gson.Gson;
import com.sterul.opencookbookapiserver.entities.RecipeImage;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.services.IllegalFiletypeException;
import com.sterul.opencookbookapiserver.services.RecipeImageService;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.springframework.beans.factory.annotation.Autowired;

public abstract class AbstractRecipeImporter implements IRecipeImporter {

    protected CloseableHttpClient client;
    protected Gson gson;

    protected AbstractRecipeImporter() {
        client = HttpClientBuilder.create().build();
        gson = new Gson();
    }

    @Autowired
    private RecipeImageService recipeImageService;

    protected RecipeImage fetchImage(String url, CookpalUser owner)
            throws UnsupportedOperationException, IllegalFiletypeException, IOException {
        var imageBytes = client.execute(new HttpGet(url), response -> EntityUtils.toByteArray(response.getEntity()));
        return recipeImageService.saveNewImage(new ByteArrayInputStream(imageBytes), imageBytes.length, owner);
    }
}
