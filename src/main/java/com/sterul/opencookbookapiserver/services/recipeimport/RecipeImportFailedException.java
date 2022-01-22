package com.sterul.opencookbookapiserver.services.recipeimport;

public class RecipeImportFailedException extends Exception {

    public RecipeImportFailedException() {
        super();
    }

    public RecipeImportFailedException(String message) {
        super(message);
    }

    public RecipeImportFailedException(String message, Throwable cause) {
        super(message, cause);
    }

}
