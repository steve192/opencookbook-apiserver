package com.sterul.opencookbookapiserver.controllers.responses;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonValue;

/** Serialised as the bare list, which is what released apps expect. */
public record ImportHostsResponse(@JsonValue List<String> hosts) {
}
