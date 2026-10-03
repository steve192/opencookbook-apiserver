package com.sterul.opencookbookapiserver.services;

import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.sterul.opencookbookapiserver.configurations.OpencookbookConfiguration;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

/**
 * Addresses in the web app, for everything this server hands out for a person to open later.
 *
 * The app's address is not something the api can work out: the two are only the same host when a
 * reverse proxy puts them there. So it is configured, and when it is not, the api's own address
 * is the least wrong guess - correct on a single origin deployment and obviously broken anywhere
 * else, which is better than a link with no host in it at all.
 *
 * Share links and the links in mails are the same problem and were once solved twice, which is
 * why the trailing slash was handled in one of them and not the other.
 *
 * The app is served under {@code /app} on the instance address, which is added to every path.
 */
@Component
@Slf4j
public class AppLinkFactory {

    @SuppressWarnings("java:S1075") // Where the web app is mounted on the instance address.
    private static final String APP_PATH = "/app";

    private final OpencookbookConfiguration configuration;

    public AppLinkFactory(OpencookbookConfiguration configuration) {
        this.configuration = configuration;
    }

    @PostConstruct
    void warnWhenTheInstanceAddressIsUnknown() {
        if (configuredInstanceUrl().isEmpty()) {
            log.warn("opencookbook.instanceURL is not set, so this instance sends no mail at all. "
                    + "Share links shown to an administrator are built from the address the api is "
                    + "reached on, which is only correct when the web app is served from the same "
                    + "origin. Set it to the address people open the app on.");
        }
    }

    /**
     * @param path an absolute path within the app, leading slash and all
     */
    public String linkTo(String path) {
        return instanceUrl() + APP_PATH + path;
    }

    /** The address links are built on, without the app path and without a trailing slash. */
    public String instanceUrl() {
        return trimTrailingSlash(configuredInstanceUrl()
                .orElseGet(() -> ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString()));
    }

    public Optional<String> configuredInstanceUrl() {
        var configuredUrl = configuration.getInstanceURL();
        return configuredUrl == null || configuredUrl.isBlank() ? Optional.empty() : Optional.of(configuredUrl);
    }

    private String trimTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
