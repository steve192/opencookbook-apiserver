package com.sterul.opencookbookapiserver.services.sharing;

import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.sterul.opencookbookapiserver.configurations.OpencookbookConfiguration;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class ShareLinkFactory {

    private static final String SHARE_PATH = "/share/";

    private final OpencookbookConfiguration configuration;

    public ShareLinkFactory(OpencookbookConfiguration configuration) {
        this.configuration = configuration;
    }

    @PostConstruct
    void warnWhenTheInstanceAddressIsUnknown() {
        if (configuredInstanceUrl().isEmpty()) {
            log.warn("opencookbook.instanceURL is not set, so share links will point at whatever "
                    + "address the api is reached on. That is only correct when the web app is "
                    + "served from the same origin - otherwise every link handed out leads to a "
                    + "server that has no /share route. Set it to the address people open the "
                    + "app on.");
        }
    }

    public String linkTo(String shareId) {
        return trimTrailingSlash(baseUrl()) + SHARE_PATH + shareId;
    }

    private String baseUrl() {
        return configuredInstanceUrl()
                .orElseGet(() -> ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString());
    }

    private Optional<String> configuredInstanceUrl() {
        var configuredUrl = configuration.getInstanceURL();
        return configuredUrl == null || configuredUrl.isBlank() ? Optional.empty() : Optional.of(configuredUrl);
    }

    private String trimTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
