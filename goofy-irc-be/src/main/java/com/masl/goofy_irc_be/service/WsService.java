package com.masl.goofy_irc_be.service;

import com.masl.goofy_irc_be.auth.GoofyAuthUser;
import com.masl.goofy_irc_be.dto.ws.*;
import com.masl.goofy_irc_be.entity.ChatRoom;
import com.masl.goofy_irc_be.exception.client.room.RoomActionNotAllowed;
import com.masl.goofy_irc_be.exception.client.room.RoomNotFound;
import com.masl.goofy_irc_be.repository.ChatRoomRepository;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

// TODO: Test properly
@Service
public class WsService {
    private static final Logger log = LoggerFactory.getLogger(WsService.class);


    private final Map<String, Map<WebSocketSession, MemberStatus>> currentHandles;
    private final ChatRoomRepository chatRoomRepository;

    public WsService(ChatRoomRepository chatRoomRepository) {
        this.chatRoomRepository = chatRoomRepository;
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
        sessions.put(session, new MemberStatus(true, false));
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
    }

    synchronized public void setStatus(String handle, WebSocketSession session, MemberStatus newStatus) {
        Map<WebSocketSession, MemberStatus> sessions = currentHandles.get(handle);
        if (sessions == null) {
            log.warn("Trying to set status without session: {}", handle);
            return;
        }

        sessions.put(session, newStatus);
    }

    synchronized public MemberStatus getStatus(String handle) {
        Map<WebSocketSession, MemberStatus> sessions = currentHandles.get(handle);
        if (sessions == null)
            return new MemberStatus(false, false);

        return combineStatusList(sessions.values().stream().toList());
    }

    synchronized public MemberStatus getStatus(String handle, WebSocketSession session) {
        Map<WebSocketSession, MemberStatus> sessions = currentHandles.get(handle);
        if (sessions == null)
            return new MemberStatus(false, false);

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

    public void trySendMessage(WsGenericEv ev, ChatRoom room) {
        trySendMessages(ev, room.getMembers(), room.getCreatedBy().getHandle());
    }

    public void trySendMessages(WsGenericEv ev, Set<String> _handles, String ... extraHandles) {
        Set<String> handles = new HashSet<>(_handles);
        handles.addAll(Arrays.asList(extraHandles));

        handles.forEach((handle) -> trySendMessage(handle, ev));
    }

    public void trySendError(WebSocketSession session, WsError err) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            session.sendMessage(new TextMessage(mapper.writeValueAsString(err)));
        } catch (IOException e) {
            log.warn("Sending Message to specific session {} failed: {}", session.getId(), e.getMessage());
        }
    }

    public void trySendMessage(String handle, String rawMessage) {
        Map<WebSocketSession, MemberStatus> sessions = currentHandles.get(handle);
        if (sessions == null || sessions.isEmpty()) {
            log.warn("Attempting to send message to handle without session");
            return;
        }

        sessions.keySet().forEach((session) -> {
            try {
                session.sendMessage(new TextMessage(rawMessage));
            } catch (IOException e) {
                log.warn("Sending Message to session {} failed: {}", session.getId(), e.getMessage());
            }
        });
    }

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
        trySendMessage(msg, room);
    }

    public void handleUpdateTypingEvent(GoofyAuthUser auth, WebSocketSession session, WsUpdateTyping ev) {
        // log.debug("Handling Update Typing Event from {}: {}", auth.getHandle(), ev);

        // Update Status
        MemberStatus status = getStatus(auth.getHandle(), session);
        if (status == null)
            return;
        status.isTyping = ev.getIsTyping();
        setStatus(auth.getHandle(), session, status);

        // Send to every member of every room the user is in
        List<ChatRoom> rooms = chatRoomRepository.findAllByCreatedBy_Handle_OrMembersContaining(auth.getHandle(), auth.getHandle());
        Set<String> updateHandles = new HashSet<>();
        for (var cRoom : rooms) {
            updateHandles.add(cRoom.getCreatedBy().getHandle());
            updateHandles.addAll(cRoom.getMembers());
        }

        // TODO: Don't send full update room and rather a specified update state message, to not DoS my server every update xd
        trySendMessages(new WsUpdateRoomList(), updateHandles);
    }

    // Helper Stuff
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MemberStatus {
        public boolean isOnline;
        public boolean isTyping;
    }

    private MemberStatus combineStatusList(List<MemberStatus> statusList) {
        boolean anyoneOnline = false; // should technically always be true but oh well
        boolean anyoneTyping = false;
        for (var status : statusList) {
            anyoneOnline |= status.isOnline;
            anyoneTyping |= status.isTyping;
        }
        return new MemberStatus(anyoneOnline, anyoneTyping);
    }
}
