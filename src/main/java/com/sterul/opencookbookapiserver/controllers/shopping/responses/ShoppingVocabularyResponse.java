package com.sterul.opencookbookapiserver.controllers.shopping.responses;

import java.util.List;

/** @param unitWords normalised, of every language */
public record ShoppingVocabularyResponse(List<ShoppingTileResponse> tiles, List<String> unitWords) {
}
