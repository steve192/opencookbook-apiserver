package com.sterul.opencookbookapiserver.unit.configurations;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;

import com.sterul.opencookbookapiserver.configurations.apikeys.ApiKeyAccess;
import com.sterul.opencookbookapiserver.configurations.apikeys.ApiKeyEndpoints;
import com.sterul.opencookbookapiserver.entities.account.ApiScope;

/** Opening one endpoint to keys must never open a neighbour whose path it happens to match. */
class ApiKeyEndpointsTest {

    static class Endpoints {
        @ApiKeyAccess(ApiScope.SHOPPING_READ)
        public void opened() {
            // Only its annotation is read.
        }

        public void closed() {
            // Only its missing annotation is read.
        }
    }

    @Test
    void aVariableThatWouldAlsoMatchAClosedEndpointRefusesToStart() throws Exception {
        var endpoints = endpoints(get("/lists/{id}"), "opened", get("/lists/special"), "closed");

        assertThatThrownBy(() -> ApiKeyEndpoints.requireNoOverlap(endpoints))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("/lists/special");
    }

    @Test
    void anEndpointOnTheSamePathThatOnlyConsumesSomethingElseRefusesToStart() throws Exception {
        var endpoints = endpoints(
                RequestMappingInfo.paths("/lists").methods(RequestMethod.POST).consumes("application/json").build(),
                "opened",
                RequestMappingInfo.paths("/lists").methods(RequestMethod.POST).consumes("multipart/form-data").build(),
                "closed");

        assertThatThrownBy(() -> ApiKeyEndpoints.requireNoOverlap(endpoints))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("multipart/form-data");
    }

    @Test
    void anotherMethodOnTheSamePathIsNoOverlap() throws Exception {
        var endpoints = endpoints(get("/lists/{id}"), "opened",
                RequestMappingInfo.paths("/lists/{id}").methods(RequestMethod.DELETE).build(), "closed");

        assertThatCode(() -> ApiKeyEndpoints.requireNoOverlap(endpoints)).doesNotThrowAnyException();
    }

    @Test
    void aMoreSpecificOpenedEndpointBesideABroadClosedOneIsFine() throws Exception {
        var endpoints = endpoints(get("/lists/current"), "opened", get("/lists/{id}"), "closed");

        assertThatCode(() -> ApiKeyEndpoints.requireNoOverlap(endpoints)).doesNotThrowAnyException();
    }

    private static RequestMappingInfo get(String path) {
        return RequestMappingInfo.paths(path).methods(RequestMethod.GET).build();
    }

    private static Map<RequestMappingInfo, HandlerMethod> endpoints(RequestMappingInfo first, String firstMethod,
            RequestMappingInfo second, String secondMethod) throws NoSuchMethodException {
        var endpoints = new LinkedHashMap<RequestMappingInfo, HandlerMethod>();
        endpoints.put(first, new HandlerMethod(new Endpoints(), Endpoints.class.getMethod(firstMethod)));
        endpoints.put(second, new HandlerMethod(new Endpoints(), Endpoints.class.getMethod(secondMethod)));
        return endpoints;
    }
}
