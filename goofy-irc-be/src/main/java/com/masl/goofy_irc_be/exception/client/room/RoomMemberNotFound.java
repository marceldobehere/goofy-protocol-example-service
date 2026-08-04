package com.masl.goofy_irc_be.exception.client.room;

import com.masl.goofy_irc_be.exception.base.BaseClientIrcException;
import com.masl.goofy_irc_be.exception.base.swagger.IrcHttpErrorCode;
import com.masl.goofy_irc_be.exception.client.AllClientErrorCodes;

import java.util.Map;

@IrcHttpErrorCode(httpStatus = 404, errorCode = AllClientErrorCodes.ROOM_MEMBER_NOT_FOUND, detailFields = {"username"})
public class RoomMemberNotFound extends BaseClientIrcException {
    public RoomMemberNotFound(String username) {
        super("Member not found: " + username, Map.of("username", username));
    }
}
