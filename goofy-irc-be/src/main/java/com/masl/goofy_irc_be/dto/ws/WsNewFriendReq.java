package com.masl.goofy_irc_be.dto.ws;

import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class WsNewFriendReq extends WsGenericEv {
    public WsNewFriendReq() {
        super(EventType.NEW_FRIEND_REQUEST);
    }
}
