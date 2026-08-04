package com.masl.goofy_irc_be.ws;

import com.masl.goofy_irc_be.auth.GoofyAuthUser;
import com.masl.goofy_irc_be.service.WsService;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;


@Component
public class MainWsHandler extends TextWebSocketHandler {
    private static final Logger log = LoggerFactory.getLogger(MainWsHandler.class);

    private final WsService wsService;

    public MainWsHandler(WsService wsService) {
        this.wsService = wsService;
    }

    @Override
    public void afterConnectionEstablished(@NonNull WebSocketSession session) throws IOException {
        log.debug("New WebSocket connection established: {}", session.getId());

        // Get Auth
        GoofyAuthUser auth = (GoofyAuthUser) session.getAttributes().get("authUser");
        if (auth == null) {
            log.warn("No authentication found for session: {}", session.getId());
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("Authentication required"));
            return;
        }

        wsService.addEntry(auth.getHandle(), session);
        log.debug("[Open] Remaining Sessions total: {}, user: {}", wsService.getEntryCount(), wsService.getEntryCount(auth.getHandle()));

    }

    @Override
    public void afterConnectionClosed(@NonNull WebSocketSession session, @NonNull CloseStatus status) {
        GoofyAuthUser auth = (GoofyAuthUser) session.getAttributes().get("authUser");
        if (auth == null) {
            log.debug("WebSocket connection closed: {} with status {}", session.getId(), status);
            return;
        }

        log.debug("WebSocket connection closed for user {} with status {}", auth.getHandle(), status);
        wsService.removeEntry(auth.getHandle(), session);
        log.debug("[Close] Remaining Sessions total: {}, user: {}", wsService.getEntryCount(), wsService.getEntryCount(auth.getHandle()));
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
