package com.masl.goofy_irc_be.exception.client.room;

import com.masl.goofy_irc_be.exception.base.BaseClientIrcException;
import com.masl.goofy_irc_be.exception.base.swagger.IrcHttpErrorCode;
import com.masl.goofy_irc_be.exception.client.AllClientErrorCodes;

import java.util.Map;

@IrcHttpErrorCode(errorCode = AllClientErrorCodes.ROOM_INVALID, detailFields = {"reason"})
public class RoomInvalid extends BaseClientIrcException {
    public RoomInvalid(String reason) {
        super("Room DTO invalid: " + reason, Map.of("reason", reason));
    }
}
