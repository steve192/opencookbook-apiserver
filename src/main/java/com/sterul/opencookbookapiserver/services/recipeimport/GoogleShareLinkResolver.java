package com.sterul.opencookbookapiserver.services.recipeimport;

import java.io.IOException;
import java.net.URI;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.util.Timeout;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

/** Chrome on Android shares a page as a share.google link, which redirects through Google to the page. */
@Component
@Slf4j
public class GoogleShareLinkResolver {

    private static final Set<String> SHARE_DOMAIN = Set.of("share.google");
    private static final Set<String> GOOGLE_DOMAINS = Set.of("share.google", "google.com");
    private static final Pattern CODE_PATH = Pattern.compile("^/([\\w-]+)/?$");
    private static final int MAX_HOPS = 3;
    private static final Timeout TIMEOUT = Timeout.ofSeconds(10);

    private CloseableHttpClient client = HttpClients.custom()
            .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
                    .setDefaultConnectionConfig(ConnectionConfig.custom().setConnectTimeout(TIMEOUT).build())
                    .build())
            .setDefaultRequestConfig(RequestConfig.custom().setResponseTimeout(TIMEOUT).build())
            .disableRedirectHandling()
            .build();

    /** The page a share.google link leads to, else the link itself; any other link as it is. */
    public String resolve(String link) {
        if (!Links.isOn(link, SHARE_DOMAIN)) {
            return link;
        }
        return pageBehind(link).orElseGet(() -> {
            log.info("No page found behind {}, importing it as it is", link);
            return link;
        });
    }

    /** Only Google is asked: each hop is followed while it stays there, and the page itself is never fetched. */
    private Optional<String> pageBehind(String link) {
        var next = shareLinkOf(link);
        for (var hop = 0; hop < MAX_HOPS && next.isPresent() && Links.isOn(next.get(), GOOGLE_DOMAINS); hop++) {
            next = redirectOf(next.get());
        }
        return next.filter(url -> !Links.isOn(url, GOOGLE_DOMAINS));
    }

    /** Rebuilt from the code, so that the link cannot lead the server anywhere else. */
    private static Optional<String> shareLinkOf(String link) {
        var code = CODE_PATH.matcher(String.valueOf(URI.create(link).getPath()));
        return code.matches() ? Optional.of("https://share.google/" + code.group(1)) : Optional.empty();
    }

    /** Empty where the answer is no redirect, or there is no answer. */
    private Optional<String> redirectOf(String url) {
        try {
            return client.execute(new HttpGet(url), response -> Optional
                    .ofNullable(response.getFirstHeader(HttpHeaders.LOCATION))
                    .map(location -> URI.create(url).resolve(location.getValue()).toString()));
        } catch (IOException | IllegalArgumentException e) {
            log.debug("Could not ask {}", url, e);
            return Optional.empty();
        }
    }

    @PreDestroy
    void closeHttpClient() {
        try {
            client.close();
        } catch (IOException e) {
            log.debug("The http client did not close cleanly", e);
        }
    }
}
