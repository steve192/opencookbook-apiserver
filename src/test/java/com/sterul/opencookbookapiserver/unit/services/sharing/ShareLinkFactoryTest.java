package com.sterul.opencookbookapiserver.unit.services.sharing;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.sterul.opencookbookapiserver.configurations.OpencookbookConfiguration;
import com.sterul.opencookbookapiserver.services.sharing.ShareLinkFactory;

/**
 * Where a share link points.
 *
 * The address of the web app is not something the api can work out - the two are only the same
 * host when a reverse proxy puts them there. Getting this wrong hands out links to a server with
 * no /share route, so both branches are worth pinning.
 */
class ShareLinkFactoryTest {

    private static final String SHARE_ID = "f88e77ba-fc97-4729-8bf7-3e0f21b55823";

    private final OpencookbookConfiguration configuration = new OpencookbookConfiguration();
    private final ShareLinkFactory cut = new ShareLinkFactory(configuration);

    @AfterEach
    void forgetTheRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void aLinkIsBuiltFromTheConfiguredInstanceAddress() {
        configuration.setInstanceURL("https://cookpal.io");

        assertEquals("https://cookpal.io/share/" + SHARE_ID, cut.linkTo(SHARE_ID));
    }

    @Test
    void anInstanceAddressWrittenWithATrailingSlashDoesNotDoubleIt() {
        configuration.setInstanceURL("https://cookpal.io/");

        assertEquals("https://cookpal.io/share/" + SHARE_ID, cut.linkTo(SHARE_ID));
    }

    @Test
    void aConfiguredAddressWinsOverTheOneTheRequestCameInOn() {
        // The api and the web app are different servers unless something puts them on one origin,
        // and it is the web app that has a /share route.
        givenARequestTo("localhost", 8080);
        configuration.setInstanceURL("http://localhost:8081");

        assertEquals("http://localhost:8081/share/" + SHARE_ID, cut.linkTo(SHARE_ID));
    }

    @Test
    void withNothingConfiguredTheLinkFallsBackToTheAddressTheRequestCameInOn() {
        givenARequestTo("localhost", 8080);

        assertEquals("http://localhost:8080/share/" + SHARE_ID, cut.linkTo(SHARE_ID));
    }

    private void givenARequestTo(String host, int port) {
        var request = new MockHttpServletRequest();
        request.setScheme("http");
        request.setServerName(host);
        request.setServerPort(port);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }
}
