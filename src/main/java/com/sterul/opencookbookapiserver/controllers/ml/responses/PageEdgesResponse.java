package com.sterul.opencookbookapiserver.controllers.ml.responses;

import java.util.List;

import com.sterul.opencookbookapiserver.services.ml.MlSubsystemProxy;

import lombok.Builder;
import lombok.Data;

/** Where the page is in a photograph, as the app is told it. */
@Data
@Builder
public class PageEdgesResponse {

    /** The four corners, clockwise from the top left, as fractions of the picture. */
    private List<double[]> corners;

    private double confidence;

    /**
     * False when the subsystem could not tell, in which case the corners are the whole frame -
     * which is what the app would have offered anyway.
     */
    private boolean detected;

    public static PageEdgesResponse from(MlSubsystemProxy.DetectedPage found) {
        return PageEdgesResponse.builder()
                .corners(found.corners())
                .confidence(found.confidence())
                .detected(found.detected())
                .build();
    }
}
