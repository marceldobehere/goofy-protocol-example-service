package com.masl.goofy_irc_be.dto.ws;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class WsUpdateRoomData extends WsGenericEv {
    public WsUpdateRoomData() {
        super(EventType.UPDATE_ROOM_DATA);
    }

    public WsUpdateRoomData(String roomName) {
        super(EventType.UPDATE_ROOM_DATA);
        this.roomName = roomName;
    }

    @NotNull
    @Pattern(regexp = "^[a-z0-9_]+$", message = "Use only a-z, 0-9, and underscore (_)")
    private String roomName;
}
