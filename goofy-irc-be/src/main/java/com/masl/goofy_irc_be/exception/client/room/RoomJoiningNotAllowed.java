package com.masl.goofy_irc_be.exception.client.room;

import com.masl.goofy_irc_be.exception.base.BaseClientIrcException;
import com.masl.goofy_irc_be.exception.base.swagger.IrcHttpErrorCode;
import com.masl.goofy_irc_be.exception.client.AllClientErrorCodes;

@IrcHttpErrorCode(errorCode = AllClientErrorCodes.ROOM_JOINING_NOT_ALLOWED)
public class RoomJoiningNotAllowed extends BaseClientIrcException {
    public RoomJoiningNotAllowed() {
        super("Joining this room is not allowed");
    }
}
