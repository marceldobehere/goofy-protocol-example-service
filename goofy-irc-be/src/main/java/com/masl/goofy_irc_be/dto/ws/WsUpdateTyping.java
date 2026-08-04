package com.masl.goofy_irc_be.dto.ws;

import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class WsUpdateTyping extends WsGenericEv {
    public WsUpdateTyping() {
        super(EventType.UPDATE_TYPING);
    }

    private String roomName;
}
