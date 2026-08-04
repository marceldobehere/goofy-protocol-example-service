package com.masl.goofy_irc_be.exception.client.room;

import com.masl.goofy_irc_be.exception.base.BaseClientIrcException;
import com.masl.goofy_irc_be.exception.base.swagger.IrcHttpErrorCode;
import com.masl.goofy_irc_be.exception.client.AllClientErrorCodes;

@IrcHttpErrorCode(errorCode = AllClientErrorCodes.ROOM_ACTION_NOT_ALLOWED, description = "The attempted action is not allowed. <br>This can include things like banning yourself, leaving a room you own, etc.")
public class RoomActionNotAllowed extends BaseClientIrcException {
    public RoomActionNotAllowed() {
        super("Attempted Action is not allowed");
    }
}
