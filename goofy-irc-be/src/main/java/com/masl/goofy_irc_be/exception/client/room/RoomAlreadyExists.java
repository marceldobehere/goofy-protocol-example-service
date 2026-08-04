package com.masl.goofy_irc_be.exception.client.room;

import com.masl.goofy_irc_be.exception.base.BaseClientIrcException;
import com.masl.goofy_irc_be.exception.base.swagger.IrcHttpErrorCode;
import com.masl.goofy_irc_be.exception.client.AllClientErrorCodes;

import java.util.Map;

@IrcHttpErrorCode(errorCode = AllClientErrorCodes.ROOM_ALREADY_EXISTS, detailFields = {"name"})
public class RoomAlreadyExists extends BaseClientIrcException {
    public RoomAlreadyExists(String name) {
        super("Room already exists: " + name, Map.of("name", name));
    }
}
