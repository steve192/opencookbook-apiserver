package com.sterul.opencookbookapiserver.services.recipeimport;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import com.google.gson.Gson;
import com.sterul.opencookbookapiserver.configurations.OpencookbookConfiguration;
import com.sterul.opencookbookapiserver.entities.RecipeImage;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.entities.recipe.Recipe;
import com.sterul.opencookbookapiserver.services.IllegalFiletypeException;
import com.sterul.opencookbookapiserver.services.RecipeImageService;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.core5.http.io.entity.EntityUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class AbstractRecipeImporter implements IRecipeImporter {

    protected CloseableHttpClient client;
    protected Gson gson;

    private final RecipeImageService recipeImageService;
    private final OpencookbookConfiguration opencookbookConfiguration;

    protected AbstractRecipeImporter(RecipeImageService recipeImageService,
            OpencookbookConfiguration opencookbookConfiguration) {
        this.recipeImageService = recipeImageService;
        this.opencookbookConfiguration = opencookbookConfiguration;
        client = HttpClientBuilder.create().build();
        gson = new Gson();
    }

    /** A picture that cannot be fetched or stored leaves the recipe without it. */
    protected void addImage(Recipe recipe, String imageUrl, CookpalUser owner) {
        if (imageUrl == null) {
            return;
        }
        try {
            recipe.getImages().add(fetchImage(imageUrl, owner));
        } catch (UnsupportedOperationException | IllegalFiletypeException | IOException e) {
            log.warn("Could not store the picture {} of {}", imageUrl, recipe.getRecipeSource(), e);
        }
    }

    private RecipeImage fetchImage(String url, CookpalUser owner) throws IOException {
        // Bound the download by the configured limit: the response is read into memory,
        // so an oversized image has to be rejected while reading, not afterwards.
        var maxImageSize = Math.toIntExact(opencookbookConfiguration.getMaxImageSize());
        var imageBytes = client.execute(new HttpGet(url),
                response -> EntityUtils.toByteArray(response.getEntity(), maxImageSize));

        return recipeImageService.saveNewImage(new ByteArrayInputStream(imageBytes), imageBytes.length, owner);
    }
}
