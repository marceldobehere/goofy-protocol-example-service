package com.masl.goofy_irc_be.exception.client.room;

import com.masl.goofy_irc_be.exception.base.BaseClientIrcException;
import com.masl.goofy_irc_be.exception.base.swagger.IrcHttpErrorCode;
import com.masl.goofy_irc_be.exception.client.AllClientErrorCodes;

import java.util.Map;

@IrcHttpErrorCode(httpStatus = 404, errorCode = AllClientErrorCodes.ROOM_NOT_FOUND, detailFields = {"name"})
public class RoomNotFound extends BaseClientIrcException {
    public RoomNotFound(String name) {
        super("Room not found: " + name, Map.of("name", name));
    }
}
