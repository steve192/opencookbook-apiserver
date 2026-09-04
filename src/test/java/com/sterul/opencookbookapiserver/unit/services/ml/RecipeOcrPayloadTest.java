package com.sterul.opencookbookapiserver.unit.services.ml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.sterul.opencookbookapiserver.services.ml.RecipeOcrPayload;

/**
 * What the app is allowed to say about the photographs it sends.
 */
class RecipeOcrPayloadTest {

    private static final String SQUARE = "[[0.1,0.1],[0.9,0.1],[0.9,0.9],[0.1,0.9]]";

    @Test
    void nothingAtAllIsAValidPayload() {
        assertNull(RecipeOcrPayload.parse(null, 1).getPages());
        assertNull(RecipeOcrPayload.parse("", 1).getPages());
    }

    @Test
    void aCropIsAccepted() {
        var payload = RecipeOcrPayload.parse("{\"pages\":[{\"crop\":" + SQUARE + "}]}", 1);

        assertNotNull(payload.getPages());
        assertEquals(1, payload.getPages().size());
        assertEquals(4, payload.getPages().get(0).getCrop().size());
    }

    @Test
    void aPageMayHaveNoCrop() {
        var payload = RecipeOcrPayload.parse("{\"pages\":[{},{}]}", 2);

        assertEquals(2, payload.getPages().size());
        assertNull(payload.getPages().get(0).getCrop());
    }

    @Test
    void describingMorePagesThanWereSentIsRefused() {
        var failure = assertThrows(IllegalArgumentException.class,
                () -> RecipeOcrPayload.parse("{\"pages\":[{},{}]}", 1));

        assertEquals("The payload describes 2 page(s) but 1 image(s) were sent",
                failure.getMessage());
    }

    @Test
    void aCropNeedsFourCorners() {
        assertThrows(IllegalArgumentException.class,
                () -> RecipeOcrPayload.parse("{\"pages\":[{\"crop\":[[0,0],[1,1]]}]}", 1));
    }

    @Test
    void cornersAreFractionsOfThePage() {
        // Pixels would break the moment the app downscaled before uploading.
        assertThrows(IllegalArgumentException.class,
                () -> RecipeOcrPayload.parse(
                        "{\"pages\":[{\"crop\":[[900,10],[0.9,0.1],[0.9,0.9],[0.1,0.9]]}]}", 1));
    }

    @Test
    void aCornerNeedsBothCoordinates() {
        assertThrows(IllegalArgumentException.class,
                () -> RecipeOcrPayload.parse(
                        "{\"pages\":[{\"crop\":[[0.1],[0.9,0.1],[0.9,0.9],[0.1,0.9]]}]}", 1));
    }

    @Test
    void somethingThatIsNotJsonIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> RecipeOcrPayload.parse("{oops", 1));
    }

    @Test
    void theLanguageTheAppAsksForIsKept() {
        // The proxy serialises this object as it stands, so keeping the value here is what
        // gets the hint as far as the subsystem.
        var payload = RecipeOcrPayload.parse(
                "{\"pages\":[{\"crop\":" + SQUARE + "}],\"language\":\"de\"}", 1);

        assertEquals("de", payload.getLanguage());
        assertEquals(4, payload.getPages().get(0).getCrop().size());
    }
}
