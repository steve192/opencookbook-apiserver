package com.sterul.opencookbookapiserver.controllers.shopping.responses;

import java.util.List;

/** @param last no older page follows */
public record ShoppingHistoryResponse(List<ShoppingListEventResponse> events, boolean last) {
}
