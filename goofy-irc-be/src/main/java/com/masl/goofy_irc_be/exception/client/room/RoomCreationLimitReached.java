package com.masl.goofy_irc_be.exception.client.room;

import com.masl.goofy_irc_be.exception.base.BaseClientIrcException;
import com.masl.goofy_irc_be.exception.base.swagger.IrcHttpErrorCode;
import com.masl.goofy_irc_be.exception.client.AllClientErrorCodes;

@IrcHttpErrorCode(errorCode = AllClientErrorCodes.ROOM_CREATION_LIMIT_REACHED)
public class RoomCreationLimitReached extends BaseClientIrcException {
    public RoomCreationLimitReached() {
        super("Room creation limit reached");
    }
}
