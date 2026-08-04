package com.masl.goofy_irc_be.ws;

import com.masl.goofy_irc_be.auth.GoofyAuthUser;
import com.masl.goofy_irc_be.dto.ws.WsError;
import com.masl.goofy_irc_be.dto.ws.WsGenericEv;
import com.masl.goofy_irc_be.dto.ws.WsSendMsg;
import com.masl.goofy_irc_be.dto.ws.WsUpdateTyping;
import com.masl.goofy_irc_be.service.WsService;
import jakarta.transaction.Transactional;
import jakarta.validation.ValidationException;
import jakarta.validation.Validator;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;


@Component
public class MainWsHandler extends TextWebSocketHandler {
    private static final Logger log = LoggerFactory.getLogger(MainWsHandler.class);

    private final WsService wsService;
    private final Validator validator;

    public MainWsHandler(WsService wsService, Validator validator) {
        this.wsService = wsService;
        this.validator = validator;
    }

    @Override
    public void afterConnectionEstablished(@NonNull WebSocketSession session) throws IOException {
        log.debug("New WebSocket connection established: {}", session.getId());

        // Get Auth
        GoofyAuthUser auth = (GoofyAuthUser) session.getAttributes().get("authUser");
        if (auth == null) {
            log.warn("No authentication found for closing session: {}", session.getId());
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("Authentication required"));
            return;
        }

        wsService.addEntry(auth.getHandle(), session);
        log.debug("[Open] Remaining Sessions total: {}, user: {}", wsService.getEntryCount(), wsService.getEntryCount(auth.getHandle()));
        wsService.handleOnlineStatusChange(auth);
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
        try {
            wsService.handleOnlineStatusChange(auth);
        } catch (CannotCreateTransactionException e) {
            // Ignore
        }
    }

    // TODO: Handle / Keep track of which rooms users have open and only send room updates when they have the room open

    @Transactional
    @Override
    protected void handleTextMessage(WebSocketSession session, @NonNull TextMessage message) throws Exception {
        GoofyAuthUser auth = (GoofyAuthUser) session.getAttributes().get("authUser");
        if (auth == null) {
            log.warn("No authentication found for session: {}", session.getId());
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("Authentication required"));
            return;
        }

        // TODO: Basic Rate Limiting

        String payload = message.getPayload();
        // log.info("Received message from {}: {}", auth.getHandle(), payload);

        ObjectMapper objectMapper = new ObjectMapper();
        WsGenericEv generic;
        try {
            generic = objectMapper.readValue(payload, WsGenericEv.class);
            if (!validator.validate(generic).isEmpty())
                throw new ValidationException("Validation failed");
        } catch (Exception e) {
            log.warn("Received message failed to parse: {}", e.getMessage());
            wsService.trySendError(session, new WsError("Received message failed to parse: " + e.getMessage()));
            return;
        }

        try {
            switch (generic.getEvType()) {
                case SEND_MSG -> {
                    WsSendMsg ev = objectMapper.readValue(payload, WsSendMsg.class);
                    if (!validator.validate(ev).isEmpty())
                        throw new ValidationException("Validation failed");
                    wsService.handleSendMessageEvent(auth, ev);
                }

                case UPDATE_TYPING -> {
                    WsUpdateTyping ev = objectMapper.readValue(payload, WsUpdateTyping.class);
                    if (!validator.validate(ev).isEmpty())
                        throw new ValidationException("Validation failed");
                    wsService.handleUpdateTypingEvent(auth, session, ev);
                }

                // TODO: Handle future messages, also need break?

                default -> {
                    log.warn("Cannot handle EventType: {}", generic.getEvType());
                    wsService.trySendError(session, new WsError("Cannot handle EventType: " + generic.getEvType()));
                }
            }
        } catch (Exception e) {
            log.warn("Handling Message failed: {}", e.getMessage());
            wsService.trySendError(session, new WsError("Handling Message failed: " + e.getMessage()));
        }
    }
}
