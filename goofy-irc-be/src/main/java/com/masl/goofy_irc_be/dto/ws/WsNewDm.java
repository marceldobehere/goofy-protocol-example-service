package com.masl.goofy_irc_be.dto.ws;

import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class WsNewDm extends WsGenericEv {
    public WsNewDm(String handleFrom) {
        super(EventType.NEW_DM);
        this.handleFrom = handleFrom;
    }

    private String handleFrom;
}
