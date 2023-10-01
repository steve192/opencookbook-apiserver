package com.sterul.opencookbookapiserver.services.recipeimport;

import java.io.IOException;

import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.springframework.beans.factory.annotation.Autowired;

import com.google.gson.Gson;
import com.sterul.opencookbookapiserver.repositories.entities.RecipeImage;
import com.sterul.opencookbookapiserver.repositories.entities.account.User;
import com.sterul.opencookbookapiserver.services.IllegalFiletypeException;
import com.sterul.opencookbookapiserver.services.RecipeImageService;

public abstract class AbstractRecipeImporter implements IRecipeImporter {

    protected CloseableHttpClient client;
    protected Gson gson;

    protected AbstractRecipeImporter() {
        client = HttpClientBuilder.create().build();
        gson = new Gson();
    }

    @Autowired
    private RecipeImageService recipeImageService;

    protected RecipeImage fetchImage(String url, User owner)
            throws UnsupportedOperationException, IllegalFiletypeException, IOException {
        var request = new HttpGet(url);
        var response = client.execute(request);

        return recipeImageService.saveNewImage(response.getEntity().getContent(),
                response.getEntity().getContentLength(), owner);
    }
}
