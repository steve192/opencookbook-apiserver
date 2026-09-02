package com.sterul.opencookbookapiserver.controllers.sharing.responses;

/** An image of a shared recipe, addressable only through the share it belongs to. */
public record SharedImageResponse(String uuid) {
}
