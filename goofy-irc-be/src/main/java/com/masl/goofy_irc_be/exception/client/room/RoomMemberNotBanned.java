package com.masl.goofy_irc_be.exception.client.room;

import com.masl.goofy_irc_be.exception.base.BaseClientIrcException;
import com.masl.goofy_irc_be.exception.base.swagger.IrcHttpErrorCode;
import com.masl.goofy_irc_be.exception.client.AllClientErrorCodes;

import java.util.Map;

@IrcHttpErrorCode(errorCode = AllClientErrorCodes.ROOM_MEMBER_NOT_BANNED, detailFields = {"username"})
public class RoomMemberNotBanned extends BaseClientIrcException {
    public RoomMemberNotBanned(String username) {
        super("Member not banned: " + username, Map.of("username", username));
    }
}
