package com.masl.goofy_irc_be.exception.client.room;

import com.masl.goofy_irc_be.exception.base.BaseClientIrcException;
import com.masl.goofy_irc_be.exception.base.swagger.IrcHttpErrorCode;
import com.masl.goofy_irc_be.exception.client.AllClientErrorCodes;

@IrcHttpErrorCode(errorCode = AllClientErrorCodes.ROOM_MEMBER_LIMIT_REACHED)
public class RoomMemberLimitReached extends BaseClientIrcException {
    public RoomMemberLimitReached() {
        super("Room member limit reached");
    }
}
