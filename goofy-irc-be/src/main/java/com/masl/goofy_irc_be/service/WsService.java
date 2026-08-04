package com.masl.goofy_irc_be.service;

import com.masl.goofy_irc_be.auth.GoofyAuthUser;
import com.masl.goofy_irc_be.dto.ws.*;
import com.masl.goofy_irc_be.entity.ChatRoom;
import com.masl.goofy_irc_be.entity.FieldSize;
import com.masl.goofy_irc_be.exception.client.room.RoomActionNotAllowed;
import com.masl.goofy_irc_be.exception.client.room.RoomNotFound;
import com.masl.goofy_irc_be.repository.ChatRoomRepository;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

// TODO: Test properly
@Service
public class WsService {
    private static final Logger log = LoggerFactory.getLogger(WsService.class);
    private static final int WS_SEND_TIMEOUT = 10_000;
    private static final int WS_BUFF_SIZE_LIMIT = FieldSize.MSG_DATA_LEN;

    private final Map<WebSocketSession, ConcurrentWebSocketSessionDecorator> sessionWrap;
    private final Map<String, Map<WebSocketSession, MemberStatus>> currentHandles;
    private final ChatRoomRepository chatRoomRepository;

    public WsService(ChatRoomRepository chatRoomRepository) {
        this.chatRoomRepository = chatRoomRepository;
        sessionWrap = new ConcurrentHashMap<>();
        currentHandles = new ConcurrentHashMap<>();
    }

    public int getEntryCount() {
        return currentHandles.size();
    }

    public int getEntryCount(String handle) {
        Map<WebSocketSession, MemberStatus> sessions = currentHandles.get(handle);
        return (sessions == null) ? 0 : sessions.size();
    }

    synchronized public void addEntry(String handle, WebSocketSession session) {
        Map<WebSocketSession, MemberStatus> sessions = currentHandles.computeIfAbsent(handle, (_) -> new ConcurrentHashMap<>());
        sessions.put(session, new MemberStatus(true, null));
        sessionWrap.put(session, new ConcurrentWebSocketSessionDecorator(session, WS_SEND_TIMEOUT, WS_BUFF_SIZE_LIMIT));
    }

    synchronized public void removeEntry(String handle, WebSocketSession session) {
        Map<WebSocketSession, MemberStatus> sessions = currentHandles.get(handle);
        if (sessions == null) {
            log.warn("Trying to remove entry without session: {}", handle);
            return;
        }

        sessions.remove(session);
        if (sessions.isEmpty())
            currentHandles.remove(handle);
        sessionWrap.remove(session);
    }

    synchronized public void setStatus(String handle, WebSocketSession session, MemberStatus newStatus) {
        Map<WebSocketSession, MemberStatus> sessions = currentHandles.get(handle);
        if (sessions == null) {
            log.warn("Trying to set status without session: {}", handle);
            return;
        }

        sessions.put(session, newStatus);
    }

    synchronized public MemberStatus getStatus(String handle, String fromRoom) {
        Map<WebSocketSession, MemberStatus> sessions = currentHandles.get(handle);
        if (sessions == null)
            return new MemberStatus(false, null);

        return combineStatusList(sessions.values().stream().toList(), fromRoom);
    }

    synchronized public MemberStatus getStatus(String handle, WebSocketSession session) {
        Map<WebSocketSession, MemberStatus> sessions = currentHandles.get(handle);
        if (sessions == null)
            return new MemberStatus(false, null);

        return sessions.get(session);
    }

    public void sendRoomListUpdate() {
        trySendMessages(new WsUpdateRoomList(), currentHandles.keySet());
    }

    public void sendRoomUpdate(String roomName, Set<String> _handles, String ... extraHandles) {
        Set<String> handles = new HashSet<>(_handles);
        handles.addAll(Arrays.asList(extraHandles));
        trySendMessages(new WsUpdateRoomData(roomName), handles);
    }

    public void trySendMessage(String handle, WsGenericEv ev) {
        ObjectMapper mapper = new ObjectMapper();
        trySendMessage(handle, mapper.writeValueAsString(ev));
    }

    public void trySendMessages(WsGenericEv ev, ChatRoom room) {
        trySendMessages(ev, room.getMembers(), room.getCreatedBy().getHandle());
    }

    public void trySendMessages(WsGenericEv ev, Set<String> _handles, String ... extraHandles) {
        Set<String> handles = new HashSet<>(_handles);
        handles.addAll(Arrays.asList(extraHandles));

        handles.forEach((handle) -> trySendMessage(handle, ev));
    }

    public void trySendError(WebSocketSession _session, WsError err) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            var session = sessionWrap.get(_session);
            session.sendMessage(new TextMessage(mapper.writeValueAsString(err)));
        } catch (IOException e) {
            log.warn("Sending Message to specific session {} failed: {}", _session.getId(), e.getMessage());
        }
    }

    public void trySendMessage(String handle, String rawMessage) {
        Map<WebSocketSession, MemberStatus> sessions = currentHandles.get(handle);
        if (sessions == null || sessions.isEmpty()) {
            log.warn("Attempting to send message to handle without session");
            return;
        }

        // TODO: Potentially parallelize?
        sessions.keySet().forEach((_session) -> {
            try {
                var session = sessionWrap.get(_session);
                session.sendMessage(new TextMessage(rawMessage));
            } catch (IOException e) {
                log.warn("Sending Message to session {} failed: {}", _session.getId(), e.getMessage());
            }
        });
    }

    // TODO: Rate Limit
    @Transactional
    public void handleSendMessageEvent(GoofyAuthUser auth, WsSendMsg ev) throws RoomNotFound, RoomActionNotAllowed {
        log.debug("Handling Send Message Event from {}: {}", auth.getHandle(), ev);

        // Check if Room exists
        ChatRoom room = chatRoomRepository.findByName(ev.getRoomName());
        if (room == null)
            throw new RoomNotFound(ev.getRoomName());

        // Check if entity owns the room or is part of the members
        if (!room.getMembers().contains(auth.getHandle()) && !auth.getHandle().equals(room.getCreatedBy().getHandle()))
            throw new RoomActionNotAllowed();

        WsReceiveMsg msg = new WsReceiveMsg();
        msg.setSenderHandle(auth.getHandle());
        msg.setRoomName(room.getName());
        msg.setMsgObj(ev.getMsgObj());
        trySendMessages(msg, room);
    }

    // TODO: Rate Limit
    @Transactional
    public void handleUpdateTypingEvent(GoofyAuthUser auth, WebSocketSession session, WsUpdateTyping ev) {
        log.info("Handling Update Typing Event from {}: {}", auth.getHandle(), ev);

        // Update Status
        MemberStatus status = getStatus(auth.getHandle(), session);
        if (status == null)
            return;

        String oldRoom = status.typingInRoom;
        status.typingInRoom = ev.getRoomName();
        setStatus(auth.getHandle(), session, status);

        // Early out
        if (Objects.equals(oldRoom, status.typingInRoom))
            return;

        // TODO: Don't send full update room and rather a specified update state message, to not DoS my server every update xd

        // Tell ppl from old room to update
        if (oldRoom != null) {
            ChatRoom room = chatRoomRepository.findByName(oldRoom);
            if (room != null)
                trySendMessages(new WsUpdateRoomData(room.getName()), room);
        }

        // Tell ppl in new room to update
        if (status.typingInRoom != null) {
            ChatRoom room = chatRoomRepository.findByName(status.typingInRoom);
            if (room != null)
                trySendMessages(new WsUpdateRoomData(room.getName()), room);
        }
    }

    // Helper Stuff
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MemberStatus {
        public boolean isOnline;
        public String typingInRoom;
    }

    private MemberStatus combineStatusList(List<MemberStatus> statusList, String room) {
        boolean anyoneOnline = false; // should technically always be true but oh well
        String typingInRoom = null;
        for (var status : statusList) {
            anyoneOnline |= status.isOnline;
            if (Objects.equals(room, status.typingInRoom))
                typingInRoom = room;
        }
        return new MemberStatus(anyoneOnline, typingInRoom);
    }
}
