package com.sterul.opencookbookapiserver.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.web.access.WebInvocationPrivilegeEvaluator;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import com.sterul.opencookbookapiserver.ratelimiting.RateLimited;

/**
 * Every endpoint somebody without an account can reach declares its rate limit tier, or is exempt below with the
 * reason, so a new public endpoint cannot ship without a budget. Tools are public here, as on cookpal.io.
 */
@SpringBootTest(properties = "opencookbook.tools.public=true")
@ActiveProfiles("integration-test")
class RateLimitCoverageIntegrationTest extends IntegrationTestBase {

    /** Public on purpose, and never limited. */
    private static final Map<String, String> EXEMPT = Map.of(
            "POST /api/v1/users/refreshToken", "every device renews every few minutes",
            "POST /api/v1/users/logout", "signing out must always work",
            "GET /api/v1/instance", "read by every app start and the sign-in screen",
            "GET /api/v1/legal/{document}", "the instance's published texts",
            "GET /api/v1/bringexport", "fetched by Bring's servers, many users behind one address");

    private static final AnonymousAuthenticationToken VISITOR = new AnonymousAuthenticationToken("visitor", "visitor",
            AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping mappings;
    @Autowired
    private WebInvocationPrivilegeEvaluator privileges;

    @Test
    void everyPublicEndpointDeclaresItsTier() {
        var undeclared = new ArrayList<String>();
        mappings.getHandlerMethods().forEach((mapping, method) -> {
            if (!method.getBeanType().getPackageName().startsWith("com.sterul") || declaresTier(method)) {
                return;
            }
            var httpMethods = mapping.getMethodsCondition().getMethods();
            for (var pattern : mapping.getPatternValues()) {
                for (var httpMethod : httpMethods.isEmpty() ? List.of(RequestMethod.GET) : httpMethods) {
                    var endpoint = httpMethod + " " + pattern;
                    if (!EXEMPT.containsKey(endpoint) && privileges.isAllowed("", concrete(pattern), httpMethod.name(),
                            VISITOR)) {
                        undeclared.add(endpoint);
                    }
                }
            }
        });

        assertEquals(List.of(), undeclared.stream().sorted().toList());
    }

    private static boolean declaresTier(HandlerMethod method) {
        return AnnotatedElementUtils.hasAnnotation(method.getMethod(), RateLimited.class)
                || AnnotatedElementUtils.hasAnnotation(method.getBeanType(), RateLimited.class);
    }

    private static String concrete(String pattern) {
        return pattern.replaceAll("\\{[^}]+}", "x").replace("/**", "/x");
    }
}
