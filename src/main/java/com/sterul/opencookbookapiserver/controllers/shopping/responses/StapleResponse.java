package com.sterul.opencookbookapiserver.controllers.shopping.responses;

import com.sterul.opencookbookapiserver.entities.shopping.ShoppingStaple;

public record StapleResponse(Long id, String name) {

    public static StapleResponse of(ShoppingStaple staple) {
        return new StapleResponse(staple.getId(), staple.getName());
    }
}
