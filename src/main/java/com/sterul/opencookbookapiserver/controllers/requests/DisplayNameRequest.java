package com.sterul.opencookbookapiserver.controllers.requests;

import jakarta.validation.constraints.Size;

/** Blank clears the name, so the account falls back to a masked address. */
public record DisplayNameRequest(@Size(max = 64) String displayName) {
}
