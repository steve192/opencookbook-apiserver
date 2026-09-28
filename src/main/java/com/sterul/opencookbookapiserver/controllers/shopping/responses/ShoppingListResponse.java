package com.sterul.opencookbookapiserver.controllers.shopping.responses;

import com.sterul.opencookbookapiserver.entities.shopping.ShoppingList;

/**
 * @param name null for an unrenamed default list, which the app names itself
 * @param householdId null for a person's own list; sent back as {@code ?household=} when acting on it
 */
public record ShoppingListResponse(Long id, String name, boolean defaultList, String householdId,
        String householdName, long version) {

    public static ShoppingListResponse of(ShoppingList list) {
        var household = list.getHousehold();
        return new ShoppingListResponse(list.getId(), list.getName(), list.isDefaultList(),
                household == null ? null : household.getId(), household == null ? null : household.getName(),
                list.getVersion());
    }
}
