package com.masl.goofy_irc_be.exception.client.room;

import com.masl.goofy_irc_be.exception.base.BaseClientIrcException;
import com.masl.goofy_irc_be.exception.base.swagger.IrcHttpErrorCode;
import com.masl.goofy_irc_be.exception.client.AllClientErrorCodes;

import java.util.Map;

@IrcHttpErrorCode(errorCode = AllClientErrorCodes.ROOM_MEMBER_ALREADY_BANNED, detailFields = {"username"})
public class RoomMemberAlreadyBanned extends BaseClientIrcException {
    public RoomMemberAlreadyBanned(String username) {
        super("Member already banned: " + username, Map.of("username", username));
    }
}
