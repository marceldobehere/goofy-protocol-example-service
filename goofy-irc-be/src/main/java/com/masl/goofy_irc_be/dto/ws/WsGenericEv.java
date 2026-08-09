package com.masl.goofy_irc_be.dto.ws;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WsGenericEv {
    @NotNull
    private WsGenericEv.EventType evType;

    public enum EventType {
        ERROR,
        SEND_MSG,
        RECEIVE_MSG,
        UPDATE_TYPING,
        UPDATE_ROOM_LIST,
        UPDATE_ROOM_DATA,
        UPDATE_IDENTITY,
        NEW_FRIEND_REQUEST,
        NEW_DM
    }
}
