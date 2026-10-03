package com.sterul.opencookbookapiserver.controllers.admin.responses;

/** @param mailed whether the link was mailed as well; it is for the administrator to hand over either way */
public record AdminPasswordResetResponse(String link, boolean mailed) {
}
