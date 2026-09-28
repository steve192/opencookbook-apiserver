package com.sterul.opencookbookapiserver.services.legal;

/**
 * A work of others this server ships or serves, with what its license asks to be passed on.
 *
 * @param licenseUrl empty where the license names none
 */
public record OpenSourceComponent(String name, String license, String licenseUrl, String author, String homepage,
        String noticeText, String licenseText) {
}
