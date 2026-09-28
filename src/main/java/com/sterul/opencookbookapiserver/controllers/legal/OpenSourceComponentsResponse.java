package com.sterul.opencookbookapiserver.controllers.legal;

import java.util.List;

import com.sterul.opencookbookapiserver.services.legal.OpenSourceComponent;

public record OpenSourceComponentsResponse(List<Section> sections) {

    /** @param id "server" or "catalogue"; the app names each in its own language */
    public record Section(String id, List<OpenSourceComponent> components) {
    }
}
