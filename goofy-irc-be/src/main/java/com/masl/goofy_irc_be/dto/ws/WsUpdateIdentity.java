package com.masl.goofy_irc_be.dto.ws;

import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class WsUpdateIdentity extends WsGenericEv {
    public WsUpdateIdentity() {
        super(EventType.UPDATE_IDENTITY);
    }

    public WsUpdateIdentity(String handle) {
        super(EventType.UPDATE_IDENTITY);
        this.handle = handle;
    }

    private String handle;
}
