package com.sterul.opencookbookapiserver.configurations.shopping;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import com.sterul.opencookbookapiserver.controllers.shopping.ShoppingPaths;
import com.sterul.opencookbookapiserver.controllers.shopping.live.ShoppingLiveHandler;

@Configuration
@EnableWebSocket
@ConditionalOnShoppingLive
public class ShoppingLiveConfiguration implements WebSocketConfigurer {

    private final ShoppingLiveHandler handler;

    public ShoppingLiveConfiguration(ShoppingLiveHandler handler) {
        this.handler = handler;
    }

    /** Any origin: the app offers its token itself (see BearerTokens), so there is no cookie for a foreign page to ride on. */
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, ShoppingPaths.LIVE).setAllowedOriginPatterns("*");
    }
}
