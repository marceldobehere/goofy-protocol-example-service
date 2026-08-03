package com.masl.goofy_irc_be.ws;

import com.masl.goofy_irc_be.auth.GoofyAuthUser;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class MainWsHandler extends TextWebSocketHandler {
    private static final Logger log = LoggerFactory.getLogger(MainWsHandler.class);

    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();

    public MainWsHandler() {
    }

    @Override
    public void afterConnectionEstablished(@NonNull WebSocketSession session) {
        log.debug("New WebSocket connection established: {}", session.getId());
        sessions.add(session);
    }

    @Override
    public void afterConnectionClosed(@NonNull WebSocketSession session, @NonNull CloseStatus status) {
        GoofyAuthUser auth = (GoofyAuthUser) session.getAttributes().get("authUser");
        if (auth == null)
            log.debug("WebSocket connection closed: {} with status {}", session.getId(), status);
        else
            log.debug("WebSocket connection closed for user {} with status {}", auth.getHandle(), status);
        sessions.remove(session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, @NonNull TextMessage message) throws Exception {
        GoofyAuthUser auth = (GoofyAuthUser) session.getAttributes().get("authUser");
        if (auth == null) {
            log.warn("No authentication found for session: {}", session.getId());
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("Authentication required"));
            return;
        }

        // TODO: Be careful because not every request comes from a registered User!
        // Some Rooms should only be for registered Users and some should allow public access
        // This is also used for notifications and dms

        String payload = message.getPayload();
        log.info("Received message from {}: {}", auth.getHandle(), payload);
        session.sendMessage(new TextMessage("echo: " + payload));
    }
}
