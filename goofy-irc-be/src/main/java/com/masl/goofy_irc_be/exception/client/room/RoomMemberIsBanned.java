package com.masl.goofy_irc_be.exception.client.room;

import com.masl.goofy_irc_be.exception.base.BaseClientIrcException;
import com.masl.goofy_irc_be.exception.base.swagger.IrcHttpErrorCode;
import com.masl.goofy_irc_be.exception.client.AllClientErrorCodes;

@IrcHttpErrorCode(errorCode = AllClientErrorCodes.ROOM_MEMBER_IS_BANNED)
public class RoomMemberIsBanned extends BaseClientIrcException {
    public RoomMemberIsBanned() {
        super("You cannot join this room because you have been banned");
    }
}
