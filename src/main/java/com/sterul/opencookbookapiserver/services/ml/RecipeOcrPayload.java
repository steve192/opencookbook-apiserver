package com.sterul.opencookbookapiserver.services.ml;

import java.util.List;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import lombok.Data;

/** What the app says about the photographs it is sending. */
@Data
public class RecipeOcrPayload {

    private static final int CORNERS_PER_CROP = 4;
    private static final Gson GSON = new Gson();

    private List<Page> pages;
    private String language;

    @Data
    public static class Page {
        private List<List<Double>> crop;
    }

    public static RecipeOcrPayload parse(String json, int imageCount) {
        if (json == null || json.isBlank()) {
            return new RecipeOcrPayload();
        }

        RecipeOcrPayload payload;
        try {
            payload = GSON.fromJson(json, RecipeOcrPayload.class);
        } catch (JsonSyntaxException e) {
            throw new IllegalArgumentException("The payload is not valid json", e);
        }
        if (payload == null) {
            return new RecipeOcrPayload();
        }

        payload.validate(imageCount);
        return payload;
    }

    private void validate(int imageCount) {
        if (pages == null) {
            return;
        }
        if (pages.size() > imageCount) {
            throw new IllegalArgumentException(
                    "The payload describes " + pages.size() + " page(s) but " + imageCount
                            + " image(s) were sent");
        }
        for (var page : pages) {
            validateCrop(page);
        }
    }

    private void validateCrop(Page page) {
        if (page == null || page.getCrop() == null) {
            return;
        }
        if (page.getCrop().size() != CORNERS_PER_CROP) {
            throw new IllegalArgumentException("A crop needs exactly four corners");
        }
        for (var corner : page.getCrop()) {
            if (corner == null || corner.size() != 2) {
                throw new IllegalArgumentException("Crop corners must be [x, y] pairs");
            }
            for (var coordinate : corner) {
                if (coordinate == null || coordinate < 0 || coordinate > 1) {
                    throw new IllegalArgumentException(
                            "Crop corners are fractions of the page and must be between 0 and 1");
                }
            }
        }
    }
}
