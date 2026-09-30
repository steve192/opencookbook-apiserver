package com.sterul.opencookbookapiserver.configurations.apikeys;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpMethod;
import org.springframework.http.server.PathContainer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.util.pattern.PathPatternParser;

/** Turns every ApiKeyAccess into a rule of the api key filter chain, with the endpoint's own paths and methods. */
public final class ApiKeyEndpoints {

    private ApiKeyEndpoints() {
    }

    public static void open(AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry authorize,
            Map<RequestMappingInfo, HandlerMethod> endpoints) {
        var opened = openedToKeys(endpoints);
        requireNoOverlap(opened, endpoints);
        opened.forEach((mapping, access) -> {
            var patterns = mapping.getPatternValues().toArray(String[]::new);
            var methods = mapping.getMethodsCondition().getMethods();
            var matchers = methods.isEmpty()
                    ? List.of(authorize.requestMatchers(patterns))
                    : methods.stream().map(method -> authorize.requestMatchers(HttpMethod.valueOf(method.name()), patterns))
                            .toList();
            var authorities = Arrays.stream(access.value()).map(ApiKeyAuthenticationToken::authorityOf)
                    .toArray(String[]::new);
            matchers.forEach(url -> {
                if (authorities.length == 0) {
                    url.authenticated();
                } else {
                    url.hasAnyAuthority(authorities);
                }
            });
        });
    }

    public static void requireNoOverlap(Map<RequestMappingInfo, HandlerMethod> endpoints) {
        requireNoOverlap(openedToKeys(endpoints), endpoints);
    }

    /**
     * A security rule is taken by the first path it matches, a request by the most specific endpoint. So
     * "/lists/{id}" opened to keys would also open an endpoint at "/lists/special"; refuse to start instead.
     */
    private static void requireNoOverlap(Map<RequestMappingInfo, ApiKeyAccess> opened,
            Map<RequestMappingInfo, HandlerMethod> endpoints) {
        opened.forEach((mapping, access) -> {
            var patterns = mapping.getPatternValues().stream().map(PathPatternParser.defaultInstance::parse).toList();
            endpoints.forEach((other, otherHandler) -> {
                var otherAccess = opened.get(other);
                var sameRule = otherAccess != null && Set.of(otherAccess.value()).equals(Set.of(access.value()));
                if (sameRule || !shareAMethod(mapping, other)) {
                    return;
                }
                var covered = other.getPatternValues().stream()
                        .anyMatch(path -> patterns.stream().anyMatch(pattern -> pattern.matches(PathContainer.parsePath(path))));
                if (covered) {
                    throw new IllegalStateException(
                            "Opening " + mapping + " to api keys would also open " + other + " (" + otherHandler + ")");
                }
            });
        });
    }

    private static Map<RequestMappingInfo, ApiKeyAccess> openedToKeys(Map<RequestMappingInfo, HandlerMethod> endpoints) {
        var opened = new LinkedHashMap<RequestMappingInfo, ApiKeyAccess>();
        endpoints.forEach((mapping, handler) -> {
            var access = handler.getMethodAnnotation(ApiKeyAccess.class);
            if (access != null) {
                opened.put(mapping, access);
            }
        });
        return opened;
    }

    private static boolean shareAMethod(RequestMappingInfo first, RequestMappingInfo second) {
        var firstMethods = first.getMethodsCondition().getMethods();
        var secondMethods = second.getMethodsCondition().getMethods();
        return firstMethods.isEmpty() || secondMethods.isEmpty() || !Collections.disjoint(firstMethods, secondMethods);
    }
}
