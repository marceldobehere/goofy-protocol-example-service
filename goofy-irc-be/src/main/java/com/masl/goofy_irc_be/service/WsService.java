package com.masl.goofy_irc_be.service;

import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

// TODO: Test properly
@Service
public class WsService {
    public record MemberStatus(boolean isOnline, boolean isTyping) {}
    private final Map<String, Map<WebSocketSession, MemberStatus>> currentHandles;

    public WsService() {
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
        if (sessions == null)
            return;

        sessions.remove(session);
        if (sessions.isEmpty())
            currentHandles.remove(handle);
    }

    synchronized public void setStatus(String handle, WebSocketSession session, MemberStatus newStatus) {
        Map<WebSocketSession, MemberStatus> sessions = currentHandles.get(handle);
        if (sessions == null)
            return;

        sessions.put(session, newStatus);
    }

    synchronized public MemberStatus getStatus(String handle) {
        Map<WebSocketSession, MemberStatus> sessions = currentHandles.get(handle);
        if (sessions == null)
            return new MemberStatus(false, false);

        return combineStatusList(sessions.values().stream().toList());
    }

    public void sendRoomListUpdate() {
        // TODO: Implement
    }

    public void sendRoomUpdate(String roomName, Set<String> _handles, String ... extraHandles) {
        Set<String> handles = new HashSet<>(_handles);
        handles.addAll(Arrays.asList(extraHandles));
        // TODO: Implement
    }

    // Helper Method
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
