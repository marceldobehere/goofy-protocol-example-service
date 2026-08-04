package com.masl.goofy_irc_be.dto.ws;

import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class WsUpdateRoomList extends WsGenericEv {
    public WsUpdateRoomList() {
        super(EventType.UPDATE_ROOM_LIST);
    }
}
