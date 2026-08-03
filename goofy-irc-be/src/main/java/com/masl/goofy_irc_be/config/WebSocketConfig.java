package com.masl.goofy_irc_be.config;

import com.masl.goofy_irc_be.ws.MainWsAuthCheck;
import com.masl.goofy_irc_be.ws.MainWsHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    private final MainWsHandler handler;
    private final MainWsAuthCheck authCheck;

    public WebSocketConfig(MainWsHandler handler, MainWsAuthCheck authCheck) {
        this.handler = handler;
        this.authCheck = authCheck;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/api/ws")
                .addInterceptors(authCheck)
                .setAllowedOrigins("*"); // This is wanted
    }
}
